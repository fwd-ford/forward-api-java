// One page of results plus the total count (sent to clients as X-Total-Count).
// Uma pagina de resultados e o total (enviado ao cliente em X-Total-Count).
package com.fwdford.forwardapi.model;

import java.util.List;

public record PageResult<T>(List<T> items, long total) {}
