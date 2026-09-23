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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Vector index of post embeddings.
 *
 * <p>The Milvus client opens its gRPC channel while it is being constructed, so it is resolved
 * lazily and only once the context is up ({@link ApplicationReadyEvent}). When Milvus is down the
 * service logs and stays disabled: reads return nothing and writes are dropped, which makes the
 * recommendation feed fall back to its chronological form instead of failing the whole service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MilvusService {

  private static final String COLLECTION_NAME = "posts";
  private static final String ID_FIELD = "post_id";
  private static final String VECTOR_FIELD = "embedding";
  private static final int VECTOR_DIMENSION = 768;

  private final ObjectProvider<MilvusServiceClient> clientProvider;

  private volatile MilvusServiceClient client;

  /** True once the collection exists and the client is usable. */
  public boolean isAvailable() {
    return client != null;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initCollection() {
    try {
      MilvusServiceClient candidate = clientProvider.getObject();
      ensureCollection(candidate);
      this.client = candidate;
      log.info("Milvus collection '{}' is ready", COLLECTION_NAME);
    } catch (Exception e) {
      log.warn(
          "Milvus is unavailable, recommendation indexing is disabled for now: {}", e.toString());
    }
  }

  private void ensureCollection(MilvusServiceClient milvusClient) {
    R<Boolean> hasCollection =
        milvusClient.hasCollection(
            HasCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());

    if (Boolean.TRUE.equals(hasCollection.getData())) {
      return;
    }

    log.info("Creating Milvus collection '{}'", COLLECTION_NAME);

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
            .withDimension(VECTOR_DIMENSION)
            .build();

    milvusClient.createCollection(
        CreateCollectionParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withDescription("Post embeddings")
            .addFieldType(postIdField)
            .addFieldType(vectorField)
            .build());

    milvusClient.createIndex(
        CreateIndexParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withFieldName(VECTOR_FIELD)
            .withIndexType(IndexType.IVF_FLAT)
            .withMetricType(MetricType.COSINE)
            .withExtraParam("{\"nlist\":1024}")
            .withSyncMode(Boolean.TRUE)
            .build());

    milvusClient.loadCollection(
        LoadCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());
  }

  /** Stores (or overwrites) the embedding of a post; a no-op while Milvus is unavailable. */
  public void insertPost(Long postId, List<Float> embedding) {
    MilvusServiceClient milvusClient = this.client;
    if (milvusClient == null) {
      log.debug("Milvus unavailable, skipping embedding of post {}", postId);
      return;
    }
    List<InsertParam.Field> fields = new ArrayList<>();
    fields.add(new InsertParam.Field(ID_FIELD, Collections.singletonList(postId)));
    fields.add(new InsertParam.Field(VECTOR_FIELD, Collections.singletonList(embedding)));

    milvusClient.insert(
        InsertParam.newBuilder().withCollectionName(COLLECTION_NAME).withFields(fields).build());
    log.debug("Inserted embedding of post {} into Milvus", postId);
  }

  /** Nearest posts for an embedding; an empty list while Milvus is unavailable. */
  public List<MilvusSearchResult> searchSimilarPosts(List<Float> userProfileEmbedding, int topK) {
    MilvusServiceClient milvusClient = this.client;
    if (milvusClient == null || userProfileEmbedding == null || userProfileEmbedding.isEmpty()) {
      return List.of();
    }
    try {
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
        return List.of();
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
    } catch (Exception e) {
      log.warn("Milvus search failed, falling back to an empty candidate set: {}", e.toString());
      return List.of();
    }
  }

  public record MilvusSearchResult(Long postId, float distance) {}
}
