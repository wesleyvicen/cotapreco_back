# Guia para agentes — backend

## Escopo

Estas instruções valem para todo o projeto `backend/`. O projeto é a API do CotaPreço, construída com Java 21, Spring Boot 3.5, Maven, Spring Security, JPA/Hibernate, Flyway e PostgreSQL.

## Estrutura do projeto

- `src/main/java/br/com/cotapreco/controller`: endpoints HTTP e autorização por perfil.
- `src/main/java/br/com/cotapreco/service`: regras de negócio e limites transacionais.
- `src/main/java/br/com/cotapreco/repository`: acesso a dados com Spring Data JPA.
- `src/main/java/br/com/cotapreco/model`: entidades persistidas.
- `src/main/java/br/com/cotapreco/dto`: contratos de entrada e saída da API.
- `src/main/java/br/com/cotapreco/security`: autenticação JWT e resolução do usuário ou representante atual.
- `src/main/resources/db/migration`: migrations versionadas do Flyway.
- `src/test`: testes unitários e de integração; os testes de integração usam H2 em modo PostgreSQL e executam as migrations reais.

## Convenções de implementação

- Mantenha nomes de domínio, classes, métodos e mensagens em português, seguindo o código existente.
- Preserve os contratos HTTP existentes. Alguns endpoints internos e campos JSON permanecem em inglês por compatibilidade; não os traduza sem uma migração de contrato explícita.
- Controllers devem cuidar do transporte, validação de entrada e autorização. Coloque regras de negócio nos services.
- Delimite leitura com `@Transactional(readOnly = true)` e alterações com `@Transactional` na camada de serviço.
- Use os DTOs para entrada e saída. Não exponha entidades JPA diretamente nos controllers.
- Lance as exceções de domínio existentes (`RegraNegocioException`, `RecursoNaoEncontradoException`, `ConflitoEstadoException` etc.) para manter o formato de erro tratado por `TratadorExcecoesApi`.
- Use `BigDecimal` para valores monetários e `Instant`/UTC para instantes persistidos ou trocados pela API.
- Respeite o isolamento por empresa em toda consulta da área da farmácia. Prefira métodos de repositório filtrados por `empresaId` e obtenha o contexto por `UsuarioAtualService`; conhecer um ID não pode permitir acesso cruzado entre empresas.
- No fluxo público, valide sempre a propriedade da resposta pelo representante autenticado. Os tokens de farmácia e representante têm contextos distintos.
- Mantenha as regras de autorização por perfil: mutações operacionais normalmente exigem `ADMIN` ou `BUYER`; `VIEWER` é somente leitura.
- Ao alterar um estado que afete plano de compra ou pedido já gerado, preserve a lógica de invalidação dos pedidos.
- Evite incluir segredos, tokens, senhas ou valores reais de `.env` no código, nos testes ou nos logs.

## Banco de dados e migrations

- Não edite migrations já aplicadas. Crie uma nova migration `V<próximo_número>__descricao_em_snake_case.sql`.
- Mantenha o schema compatível com PostgreSQL e com os testes H2 em modo PostgreSQL.
- Se adicionar uma coluna obrigatória a dados existentes, faça o preenchimento dos registros antes de aplicar `NOT NULL`.
- Deixe `spring.jpa.hibernate.ddl-auto=validate`; mudanças de schema devem passar pelo Flyway.
- Atualize entidades, constraints, repositórios e testes junto com a migration.

## Testes e verificação

Execute a partir de `backend/`:

```bash
mvn test
mvn package
```

Durante a implementação, um teste específico pode ser executado com:

```bash
mvn -Dtest=NomeDoTeste test
```

- Adicione ou ajuste testes para toda mudança de regra de negócio, autorização, isolamento de dados, validação ou persistência.
- Prefira testes de serviço para regras isoladas e testes de integração para contratos HTTP, segurança, JPA e migrations.
- Cubra tanto o caminho válido quanto os principais casos de erro e acesso indevido.
- Antes de concluir, confirme que `mvn test` passa. Para mudanças que afetam empacotamento ou configuração, execute também `mvn package`.

## Execução local

O backend espera PostgreSQL e usa, por padrão, a porta `8080`:

```bash
mvn spring-boot:run
```

As configurações devem vir das variáveis documentadas no `README.md` da raiz e no `.env.example`. Não dependa do `.env` local de um desenvolvedor para testes reproduzíveis.
