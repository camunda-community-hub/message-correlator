package org.camunda.community.messagecorrelator;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

public class MessageBody {

  public static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

  @JsonFormat(pattern = DATE_PATTERN)
  private Date date;

  private String message;
  private boolean isSynthetic;

  public String getMessage() {
    return message;
  }

  public MessageBody setMessage(String message) {
    this.message = message;
    return this;
  }

  @Override
  public String toString() {
    return "MessageBody{"
        + "date="
        + date
        + ", message='"
        + message
        + '\''
        + ", isSynthetic="
        + isSynthetic
        + '}';
  }

  public Date getDate() {
    return date;
  }

  public MessageBody setDate(Date date) {
    this.date = date;
    return this;
  }

  public boolean isSynthetic() {
    return isSynthetic;
  }

  public MessageBody setSynthetic(boolean synthetic) {
    isSynthetic = synthetic;
    return this;
  }
}
