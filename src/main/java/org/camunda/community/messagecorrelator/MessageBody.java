package org.camunda.community.messagecorrelator;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.apache.commons.lang3.builder.MultilineRecursiveToStringStyle;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class MessageBody {

  public static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

  @JsonFormat(pattern = DATE_PATTERN)
  private Date date;

  private String message;
  private boolean isSynthetic;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(
        this,
        new MultilineRecursiveToStringStyle() {
          public ToStringStyle withShortPrefixes() {
            this.setUseShortClassName(true);
            this.setUseIdentityHashCode(false);
            return this;
          }
        }.withShortPrefixes());
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public boolean isSynthetic() {
    return isSynthetic;
  }

  public void setSynthetic(boolean synthetic) {
    isSynthetic = synthetic;
  }
}