package com.fwdford.forwardapi.it;

/** Identifiers from src/main/resources/db/seed/R__seed_demo_data.sql. */
public final class TestData {

  public static final String PASSWORD = "Forward@2026";

  public static final String ADMIN_ID = "ad000000-0000-4000-8000-000000000001";
  public static final String GESTOR_ID = "ad000000-0000-4000-8000-000000000002";
  public static final String ATENDENTE_ID = "ad000000-0000-4000-8000-000000000003";
  public static final String ATENDENTE_2_ID = "ad000000-0000-4000-8000-000000000004";
  public static final String INACTIVE_ID = "ad000000-0000-4000-8000-000000000006";

  public static final String DEALER_1 = "d0000000-0000-4000-8000-000000000001";
  public static final String DEALER_2 = "d0000000-0000-4000-8000-000000000002";

  /** Dealer F0001: 8 leads. Dealer F0002: 5 leads. Total: 18 leads. */
  public static final int LEADS_DEALER_1 = 8;

  public static final int LEADS_DEALER_2 = 5;
  public static final int LEADS_TOTAL = 18;

  public static final String LEAD_NEW_DEALER_1 = "a1000000-0000-4000-8000-000000000001";
  public static final String LEAD_CONTACTED_DEALER_1 = "a1000000-0000-4000-8000-000000000004";
  public static final String LEAD_CONVERTED_DEALER_1 = "a1000000-0000-4000-8000-000000000005";
  public static final String LEAD_EXPIRED_DEALER_1 = "a1000000-0000-4000-8000-000000000008";
  public static final String LEAD_NEW_DEALER_2 = "a1000000-0000-4000-8000-000000000009";

  public static final String CUSTOMER_DEALER_1 = "11111111-1111-1111-1111-111111111001";
  public static final String CUSTOMER_DEALER_2 = "11111111-1111-1111-1111-111111111003";

  public static final String VIN_DEALER_1 = "9BFZZZ5SZJB000001";
  public static final String VIN_DEALER_1_B = "9BFZZZ5SZJB000011";
  public static final String VIN_DEALER_2 = "9BFZZZ5SZJB000003";
  public static final String VIN_UNKNOWN = "9BFZZZ5SZJB999999";

  public static final String EVENT_DEALER_1 = "5e000000-0000-4000-8000-000000000001";
  public static final String EVENT_DEALER_2 = "5e000000-0000-4000-8000-000000000007";

  /** Service orders of dealer F0001 in the seed. */
  public static final int EVENTS_DEALER_1 = 5;

  public static final String UNKNOWN_UUID = "00000000-0000-4000-8000-000000000000";

  private TestData() {}
}
