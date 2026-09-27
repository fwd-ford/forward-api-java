# Cobertura de testes (JaCoCo)

Execução: `./mvnw -B -ntp spotless:check && ./mvnw -B -ntp clean verify -P quality` no commit `b333df0` (feat/sprint3-soa-jwt-rbac). Suíte: 217 testes (119 unitários + 98 de integração HTTP), 0 falhas.

Relatório completo: `target/site/jacoco/index.html` (gerado na fase `verify`).

| Pacote | Instruções | Branches | Linhas | Métodos |
|---|---|---|---|---|
| `com.fwdford.forwardapi` | 37.5% | - | 33.3% | 50.0% |
| `com.fwdford.forwardapi.config` | 98.7% | 50.0% | 99.1% | 100.0% |
| `com.fwdford.forwardapi.error` | 64.4% | 43.5% | 64.4% | 75.4% |
| `com.fwdford.forwardapi.model` | 95.6% | - | 93.5% | 88.9% |
| `com.fwdford.forwardapi.repository` | 98.3% | 68.8% | 99.3% | 100.0% |
| `com.fwdford.forwardapi.security` | 96.3% | 83.9% | 94.9% | 97.6% |
| `com.fwdford.forwardapi.service` | 95.5% | 84.0% | 96.3% | 87.9% |
| `com.fwdford.forwardapi.soap` | 96.1% | 50.0% | 97.4% | 100.0% |
| `com.fwdford.forwardapi.util` | 100.0% | 100.0% | 100.0% | 100.0% |
| `com.fwdford.forwardapi.web` | 95.0% | 80.0% | 98.4% | 96.3% |
| `com.fwdford.forwardapi.web.dto` | 89.1% | 90.0% | 85.2% | 75.0% |
| **Total** | **92.1%** | **76.0%** | **92.4%** | **91.1%** |

O pacote raiz contém apenas o `main` do Spring Boot. Os testes de integração sobem a aplicação inteira (filtros, Spring Security, controllers, serviços, repositórios e banco), por isso a cobertura reflete o comportamento real das rotas.
