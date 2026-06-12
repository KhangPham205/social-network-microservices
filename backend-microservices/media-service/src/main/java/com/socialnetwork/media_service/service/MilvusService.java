package com.socialnetwork.media_service.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilvusService {

  private final MilvusServiceClient milvusClient;
  private static final String COLLECTION_NAME = "posts";
  private static final String ID_FIELD = "post_id";
  private static final String VECTOR_FIELD = "embedding";

  @PostConstruct
  public void initCollection() {
    R<Boolean> hasCollection =
        milvusClient.hasCollection(
            HasCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());

    if (hasCollection.getData() != null && hasCollection.getData()) {
      return;
    }

    log.info("Creating Milvus collection: {}", COLLECTION_NAME);

    FieldType postIdField =
        FieldType.newBuilder()
            .withName(ID_FIELD)
            .withDataType(DataType.Int64)
            .withPrimaryKey(true)
            .withAutoID(false)
            .build();

    FieldType vectorField =
        FieldType.newBuilder()
            .withName(VECTOR_FIELD)
            .withDataType(DataType.FloatVector)
            .withDimension(768)
            .build();

    CreateCollectionParam createParam =
        CreateCollectionParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withDescription("Post embeddings")
            .addFieldType(postIdField)
            .addFieldType(vectorField)
            .build();

    milvusClient.createCollection(createParam);

    CreateIndexParam createIndexParam =
        CreateIndexParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withFieldName(VECTOR_FIELD)
            .withIndexType(IndexType.IVF_FLAT)
            .withMetricType(MetricType.COSINE)
            .withExtraParam("{\"nlist\":1024}")
            .withSyncMode(Boolean.TRUE)
            .build();

    milvusClient.createIndex(createIndexParam);

    milvusClient.loadCollection(
        LoadCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());

    log.info("Milvus collection created and loaded successfully!");
  }

  public void insertPost(Long postId, List<Float> embedding) {
    List<InsertParam.Field> fields = new ArrayList<>();
    fields.add(new InsertParam.Field(ID_FIELD, Collections.singletonList(postId)));
    fields.add(new InsertParam.Field(VECTOR_FIELD, Collections.singletonList(embedding)));

    InsertParam insertParam =
        InsertParam.newBuilder().withCollectionName(COLLECTION_NAME).withFields(fields).build();

    milvusClient.insert(insertParam);
    log.info("Inserted post {} embedding to Milvus", postId);
  }

  public List<MilvusSearchResult> searchSimilarPosts(List<Float> userProfileEmbedding, int topK) {
    SearchParam searchParam =
        SearchParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withMetricType(MetricType.COSINE)
            .withTopK(topK)
            .withVectors(Collections.singletonList(userProfileEmbedding))
            .withVectorFieldName(VECTOR_FIELD)
            .addOutField(ID_FIELD)
            .build();

    R<io.milvus.grpc.SearchResults> response = milvusClient.search(searchParam);

    if (response.getData() == null) {
      return Collections.emptyList();
    }

    SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
    List<MilvusSearchResult> results = new ArrayList<>();

    if (wrapper.getRowRecords() != null && !wrapper.getRowRecords().isEmpty()) {
      for (int i = 0; i < wrapper.getRowRecords().size(); i++) {
        long postId = (long) wrapper.getFieldWrapper(ID_FIELD).getFieldData().get(i);
        float distance = wrapper.getIDScore(0).get(i).getScore();
        results.add(new MilvusSearchResult(postId, distance));
      }
    }
    return results;
  }

  public record MilvusSearchResult(Long postId, float distance) {}
}
