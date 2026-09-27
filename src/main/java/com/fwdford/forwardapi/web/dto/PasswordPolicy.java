// Validation constants shared by user creation and password reset.
// Constantes de validacao compartilhadas entre criacao e redefinicao de senha.
package com.fwdford.forwardapi.web.dto;

public final class PasswordPolicy {

  /** At least one lowercase, one uppercase, one digit and one symbol; 8 to 72 chars. */
  public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";

  public static final String MESSAGE =
      "senha fraca: use de 8 a 72 caracteres com letra maiúscula, minúscula, número e símbolo";

  public static final String DESCRIPTION =
      "Mínimo 8 caracteres, com letra maiúscula, minúscula, número e símbolo.";

  public static final String UUID_REGEX =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  private PasswordPolicy() {}
}
