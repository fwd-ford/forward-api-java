# Cobertura de testes (JaCoCo)

Execução: `./mvnw -B -ntp spotless:check && ./mvnw -B -ntp clean verify -P quality` no commit `bf2f0e2` (feat/sprint3-soa-jwt-rbac). Suíte: 171 testes (84 unitários + 87 de integração HTTP), 0 falhas.

Relatório completo: `target/site/jacoco/index.html` (gerado na fase `verify`).

| Pacote | Instruções | Branches | Linhas | Métodos |
|---|---|---|---|---|
| `com.fwdford.forwardapi` | 37.5% | - | 33.3% | 50.0% |
| `com.fwdford.forwardapi.config` | 98.7% | 50.0% | 99.1% | 100.0% |
| `com.fwdford.forwardapi.error` | 57.4% | 35.3% | 55.9% | 70.5% |
| `com.fwdford.forwardapi.model` | 95.6% | - | 93.5% | 88.9% |
| `com.fwdford.forwardapi.repository` | 98.2% | 68.8% | 99.2% | 100.0% |
| `com.fwdford.forwardapi.security` | 95.2% | 78.7% | 93.1% | 96.9% |
| `com.fwdford.forwardapi.service` | 94.3% | 80.3% | 95.6% | 87.9% |
| `com.fwdford.forwardapi.soap` | 96.2% | 50.0% | 97.6% | 100.0% |
| `com.fwdford.forwardapi.web` | 92.2% | 72.9% | 95.9% | 96.3% |
| `com.fwdford.forwardapi.web.dto` | 85.7% | 30.0% | 85.2% | 75.0% |
| **Total** | **89.9%** | **68.8%** | **90.1%** | **89.6%** |

O pacote raiz contém apenas o `main` do Spring Boot. Os testes de integração sobem a aplicação inteira (filtros, Spring Security, controllers, serviços, repositórios e banco), por isso a cobertura reflete o comportamento real das rotas.
