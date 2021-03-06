/*
Copyright 2020 The Kubernetes Authors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.kubernetes.client.openapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.gsonfire.GsonFireBuilder;
import io.kubernetes.client.gson.V1StatusPreProcessor;
import io.kubernetes.client.openapi.models.V1Status;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import okio.ByteString;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;

public class JSON {
  private Gson gson;
  private boolean isLenientOnJson = false;
  private DateTypeAdapter dateTypeAdapter = new DateTypeAdapter();
  private SqlDateTypeAdapter sqlDateTypeAdapter = new SqlDateTypeAdapter();
  private DateTimeTypeAdapter dateTimeTypeAdapter = new DateTimeTypeAdapter();
  private LocalDateTypeAdapter localDateTypeAdapter = new LocalDateTypeAdapter();
  private ByteArrayAdapter byteArrayAdapter = new ByteArrayAdapter();

  public static GsonBuilder createGson() {
    GsonFireBuilder fireBuilder = new GsonFireBuilder();
    GsonBuilder builder =
        fireBuilder
            .registerPreProcessor(V1Status.class, new V1StatusPreProcessor())
            .createGsonBuilder();
    return builder;
  }

  private static String getDiscriminatorValue(JsonElement readElement, String discriminatorField) {
    JsonElement element = readElement.getAsJsonObject().get(discriminatorField);
    if (null == element) {
      throw new IllegalArgumentException(
          "missing discriminator field: <" + discriminatorField + ">");
    }
    return element.getAsString();
  }

  private static Class getClassByDiscriminator(
      Map classByDiscriminatorValue, String discriminatorValue) {
    Class clazz =
        (Class) classByDiscriminatorValue.get(discriminatorValue.toUpperCase(Locale.ROOT));
    if (null == clazz) {
      throw new IllegalArgumentException(
          "cannot determine model class of name: <" + discriminatorValue + ">");
    }
    return clazz;
  }

  public JSON() {
    gson =
        createGson()
            .registerTypeAdapter(Date.class, dateTypeAdapter)
            .registerTypeAdapter(java.sql.Date.class, sqlDateTypeAdapter)
            .registerTypeAdapter(DateTime.class, dateTimeTypeAdapter)
            .registerTypeAdapter(LocalDate.class, localDateTypeAdapter)
            .registerTypeAdapter(byte[].class, byteArrayAdapter)
            .create();
  }

  /**
   * Get Gson.
   *
   * @return Gson
   */
  public Gson getGson() {
    return gson;
  }

  /**
   * Set Gson.
   *
   * @param gson Gson
   * @return JSON
   */
  public JSON setGson(Gson gson) {
    this.gson = gson;
    return this;
  }

  public JSON setLenientOnJson(boolean lenientOnJson) {
    isLenientOnJson = lenientOnJson;
    return this;
  }

  /**
   * Serialize the given Java object into JSON string.
   *
   * @param obj Object
   * @return String representation of the JSON
   */
  public String serialize(Object obj) {
    return gson.toJson(obj);
  }

  /**
   * Deserialize the given JSON string to Java object.
   *
   * @param <T> Type
   * @param body The JSON string
   * @param returnType The type to deserialize into
   * @return The deserialized Java object
   */
  @SuppressWarnings("unchecked")
  public <T> T deserialize(String body, Type returnType) {
    try {
      if (isLenientOnJson) {
        JsonReader jsonReader = new JsonReader(new StringReader(body));
        // see
        // https://google-gson.googlecode.com/svn/trunk/gson/docs/javadocs/com/google/gson/stream/JsonReader.html#setLenient(boolean)
        jsonReader.setLenient(true);
        return gson.fromJson(jsonReader, returnType);
      } else {
        return gson.fromJson(body, returnType);
      }
    } catch (JsonParseException e) {
      // Fallback processing when failed to parse JSON form response body:
      // return the response body string directly for the String return type;
      if (returnType.equals(String.class)) {
        return (T) body;
      } else {
        throw (e);
      }
    }
  }

  /** Gson TypeAdapter for Byte Array type */
  public class ByteArrayAdapter extends TypeAdapter<byte[]> {

    @Override
    public void write(JsonWriter out, byte[] value) throws IOException {
      boolean oldHtmlSafe = out.isHtmlSafe();
      out.setHtmlSafe(false);
      if (value == null) {
        out.nullValue();
      } else {
        out.value(ByteString.of(value).base64());
      }
      out.setHtmlSafe(oldHtmlSafe);
    }

    @Override
    public byte[] read(JsonReader in) throws IOException {
      switch (in.peek()) {
        case NULL:
          in.nextNull();
          return null;
        default:
          String bytesAsBase64 = in.nextString();
          ByteString byteString = ByteString.decodeBase64(bytesAsBase64);
          return byteString.toByteArray();
      }
    }
  }

  /** Gson TypeAdapter for Joda DateTime type */
  public static class DateTimeTypeAdapter extends TypeAdapter<DateTime> {

    private DateTimeFormatter formatter;

    public DateTimeTypeAdapter() {
      this(
          new DateTimeFormatterBuilder()
              .append(
                  ISODateTimeFormat.dateTime().getPrinter(),
                  ISODateTimeFormat.dateOptionalTimeParser().getParser())
              .toFormatter());
    }

    public DateTimeTypeAdapter(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    public void setFormat(DateTimeFormatter dateFormat) {
      this.formatter = dateFormat;
    }

    @Override
    public void write(JsonWriter out, DateTime date) throws IOException {
      if (date == null) {
        out.nullValue();
      } else {
        out.value(formatter.print(date));
      }
    }

    @Override
    public DateTime read(JsonReader in) throws IOException {
      switch (in.peek()) {
        case NULL:
          in.nextNull();
          return null;
        default:
          String date = in.nextString();
          return formatter.parseDateTime(date);
      }
    }
  }

  /** Gson TypeAdapter for Joda LocalDate type */
  public class LocalDateTypeAdapter extends TypeAdapter<LocalDate> {

    private DateTimeFormatter formatter;

    public LocalDateTypeAdapter() {
      this(ISODateTimeFormat.date());
    }

    public LocalDateTypeAdapter(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    public void setFormat(DateTimeFormatter dateFormat) {
      this.formatter = dateFormat;
    }

    @Override
    public void write(JsonWriter out, LocalDate date) throws IOException {
      if (date == null) {
        out.nullValue();
      } else {
        out.value(formatter.print(date));
      }
    }

    @Override
    public LocalDate read(JsonReader in) throws IOException {
      switch (in.peek()) {
        case NULL:
          in.nextNull();
          return null;
        default:
          String date = in.nextString();
          return formatter.parseLocalDate(date);
      }
    }
  }

  public JSON setDateTimeFormat(DateTimeFormatter dateFormat) {
    dateTimeTypeAdapter.setFormat(dateFormat);
    return this;
  }

  public JSON setLocalDateFormat(DateTimeFormatter dateFormat) {
    localDateTypeAdapter.setFormat(dateFormat);
    return this;
  }

  /**
   * Gson TypeAdapter for java.sql.Date type If the dateFormat is null, a simple "yyyy-MM-dd" format
   * will be used (more efficient than SimpleDateFormat).
   */
  public static class SqlDateTypeAdapter extends TypeAdapter<java.sql.Date> {

    private DateFormat dateFormat;

    public SqlDateTypeAdapter() {}

    public SqlDateTypeAdapter(DateFormat dateFormat) {
      this.dateFormat = dateFormat;
    }

    public void setFormat(DateFormat dateFormat) {
      this.dateFormat = dateFormat;
    }

    @Override
    public void write(JsonWriter out, java.sql.Date date) throws IOException {
      if (date == null) {
        out.nullValue();
      } else {
        String value;
        if (dateFormat != null) {
          value = dateFormat.format(date);
        } else {
          value = date.toString();
        }
        out.value(value);
      }
    }

    @Override
    public java.sql.Date read(JsonReader in) throws IOException {
      switch (in.peek()) {
        case NULL:
          in.nextNull();
          return null;
        default:
          String date = in.nextString();
          try {
            if (dateFormat != null) {
              return new java.sql.Date(dateFormat.parse(date).getTime());
            }
            return new java.sql.Date(ISODateTimeFormat.basicDateTime().parseMillis(date));
          } catch (ParseException e) {
            throw new JsonParseException(e);
          }
      }
    }
  }

  /**
   * Gson TypeAdapter for java.util.Date type If the dateFormat is null, ISO8601Utils will be used.
   */
  public static class DateTypeAdapter extends TypeAdapter<Date> {

    private DateFormat dateFormat;

    public DateTypeAdapter() {}

    public DateTypeAdapter(DateFormat dateFormat) {
      this.dateFormat = dateFormat;
    }

    public void setFormat(DateFormat dateFormat) {
      this.dateFormat = dateFormat;
    }

    @Override
    public void write(JsonWriter out, Date date) throws IOException {
      if (date == null) {
        out.nullValue();
      } else {
        String value;
        if (dateFormat != null) {
          value = dateFormat.format(date);
        } else {
          value = ISODateTimeFormat.basicDateTime().print(date.getTime());
        }
        out.value(value);
      }
    }

    @Override
    public Date read(JsonReader in) throws IOException {
      try {
        switch (in.peek()) {
          case NULL:
            in.nextNull();
            return null;
          default:
            String date = in.nextString();
            try {
              if (dateFormat != null) {
                return dateFormat.parse(date);
              }
              return ISODateTimeFormat.basicDateTime().parseDateTime(date).toDate();
            } catch (ParseException e) {
              throw new JsonParseException(e);
            }
        }
      } catch (IllegalArgumentException e) {
        throw new JsonParseException(e);
      }
    }
  }

  public JSON setDateFormat(DateFormat dateFormat) {
    dateTypeAdapter.setFormat(dateFormat);
    return this;
  }

  public JSON setSqlDateFormat(DateFormat dateFormat) {
    sqlDateTypeAdapter.setFormat(dateFormat);
    return this;
  }
}
