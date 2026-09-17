// package utils;
//
// import com.kt.social.domain.friendship.repository.FriendshipRepository;
// import org.springframework.stereotype.Component;
//
// import java.util.HashSet;
// import java.util.List;
// import java.util.Set;
//
// @Component
// public class BlockUtils {
//
//    private final FriendshipRepository friendshipRepository;
//
//    public BlockUtils(FriendshipRepository friendshipRepository) {
//        this.friendshipRepository = friendshipRepository;
//    }
//
//    public Set<Long> getAllBlockedIds(Long userId) {
//        return new HashSet<>(friendshipRepository.findBlockedUserIds(userId));
//    }
//
//    public boolean isBlocked(Long currentUserId, Long targetId) {
//        List<Long> blocked = friendshipRepository.findBlockedUserIds(currentUserId);
//        return blocked.contains(targetId);
//    }
// }
