package com.socialnetwork.common.vo;

import java.util.List;

/**
 * Cursor-based page.
 *
 * @param nextCursor id of the last element of this page, or {@code null} when exhausted
 */
public record CursorPage<T>(List<T> content, String nextCursor) {}
