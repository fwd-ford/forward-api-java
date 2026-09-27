# Cobertura de testes (JaCoCo)

Execução: `./mvnw -B -ntp spotless:check && ./mvnw -B -ntp clean verify -P quality` no commit `039630d` (feat/sprint3-soa-jwt-rbac). Suíte: 192 testes (102 unitários + 90 de integração HTTP), 0 falhas.

Relatório completo: `target/site/jacoco/index.html` (gerado na fase `verify`).

| Pacote | Instruções | Branches | Linhas | Métodos |
|---|---|---|---|---|
| `com.fwdford.forwardapi` | 37.5% | - | 33.3% | 50.0% |
| `com.fwdford.forwardapi.config` | 98.7% | 50.0% | 99.1% | 100.0% |
| `com.fwdford.forwardapi.error` | 64.4% | 43.5% | 64.4% | 75.4% |
| `com.fwdford.forwardapi.model` | 95.6% | - | 93.5% | 88.9% |
| `com.fwdford.forwardapi.repository` | 98.2% | 68.8% | 99.2% | 100.0% |
| `com.fwdford.forwardapi.security` | 95.6% | 81.2% | 93.9% | 97.1% |
| `com.fwdford.forwardapi.service` | 94.3% | 80.3% | 95.6% | 87.9% |
| `com.fwdford.forwardapi.soap` | 96.1% | 50.0% | 97.4% | 100.0% |
| `com.fwdford.forwardapi.util` | 100.0% | 100.0% | 100.0% | 100.0% |
| `com.fwdford.forwardapi.web` | 95.0% | 80.0% | 98.4% | 96.3% |
| `com.fwdford.forwardapi.web.dto` | 85.7% | 30.0% | 85.2% | 75.0% |
| **Total** | **91.3%** | **72.1%** | **91.8%** | **90.7%** |

O pacote raiz contém apenas o `main` do Spring Boot. Os testes de integração sobem a aplicação inteira (filtros, Spring Security, controllers, serviços, repositórios e banco), por isso a cobertura reflete o comportamento real das rotas.
