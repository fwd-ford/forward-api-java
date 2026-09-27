// Pagination conventions shared by collection endpoints: JSON array body plus the total
// number of matching items in the X-Total-Count response header.
// Convencoes de paginacao: corpo em array JSON e total no header X-Total-Count.
package com.fwdford.forwardapi.web;

public final class Pagination {

  public static final String TOTAL_COUNT = "X-Total-Count";

  private Pagination() {}
}
