// Input validation helpers used by controllers. Format and whitelist checks only.
// Helpers de validacao usados pelos controllers: formato e whitelist.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.error.ApiException;
import java.util.List;
import java.util.regex.Pattern;

public final class Validations {

  private static final Pattern UUID_RE =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final Pattern VIN_RE = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

  private Validations() {}

  // validateUuid ensures the value is an RFC 4122 UUID.
  // validateUuid: garante que o valor eh um UUID valido.
  public static String validateUuid(String name, String value) {
    String v = value == null ? "" : value.trim();
    if (!UUID_RE.matcher(v).matches()) {
      throw ApiException.badRequest("Parametro " + name + " deve ser um UUID valido.");
    }
    return v;
  }

  // validateVin ensures a 17-char ISO 3779 VIN, uppercased, excluding I/O/Q.
  // validateVin: garante VIN ISO 3779 de 17 chars sem I/O/Q.
  public static String validateVin(String value) {
    String v = value == null ? "" : value.trim().toUpperCase();
    if (!VIN_RE.matcher(v).matches()) {
      throw ApiException.badRequest("VIN deve ter 17 caracteres validos (sem I, O ou Q).");
    }
    return v;
  }

  // validateLimit clamps an integer query parameter to [1, max] with a default.
  // validateLimit: normaliza parametro de limite para [1, max].
  public static int validateLimit(String raw, int defaultValue, int max) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    int n;
    try {
      n = Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest("Parametro limit deve ser inteiro positivo.");
    }
    if (n < 1) {
      throw ApiException.badRequest("Parametro limit deve ser inteiro positivo.");
    }
    return Math.min(n, max);
  }

  // Integer overload used when Spring auto-parses the query param.
  // Sobrecarga usada quando o Spring ja converte o parametro para Integer.
  public static int validateLimit(Integer raw, int defaultValue, int max) {
    if (raw == null) {
      return defaultValue;
    }
    if (raw < 1) {
      throw ApiException.badRequest("Parametro limit deve ser inteiro positivo.");
    }
    return Math.min(raw, max);
  }

  // validateEnum ensures the value is in the whitelist when not empty.
  // validateEnum: valida que o valor esta na whitelist quando nao vazio.
  public static String validateEnum(String name, String value, List<String> allowed) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    if (!allowed.contains(value)) {
      throw ApiException.badRequest("Valor invalido para " + name + ".");
    }
    return value;
  }
}
