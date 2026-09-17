package exception;

import java.util.Date;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException; // Chú ý import này
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice // Đánh dấu đây là trình xử lý lỗi toàn cục
public class GlobalExceptionHandler {

  // Bắt lỗi 404 (do chúng ta tự ném ra)
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
      ResourceNotFoundException ex, WebRequest request) {
    ErrorResponse errorDetails =
        new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage(), new Date().getTime());
    return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
  }

  // Bắt lỗi 400 (do chúng ta tự ném ra, ví dụ: validation)
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponse> handleBadRequestException(
      BadRequestException ex, WebRequest request) {
    ErrorResponse errorDetails =
        new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), new Date().getTime());
    return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
  }

  // Bắt lỗi 403 (Forbidden) từ Spring Security
  // Đây là khi người dùng ĐÃ ĐĂNG NHẬP nhưng không có quyền
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDeniedException(
      AccessDeniedException ex, WebRequest request) {
    ErrorResponse errorDetails =
        new ErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "Bạn không có quyền truy cập tài nguyên này.", // Ghi đè thông báo mặc định
            new Date().getTime());
    return new ResponseEntity<>(errorDetails, HttpStatus.FORBIDDEN);
  }

  // Bắt tất cả các lỗi 500 (lỗi máy chủ)
  // Đây là "chốt chặn" cuối cùng
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
    ErrorResponse errorDetails =
        new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Lỗi máy chủ nội bộ: " + ex.getMessage(),
            new Date().getTime());
    // Quan trọng: Log lỗi này ra để debug
    // log.error("Unhandled exception: ", ex);
    return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
