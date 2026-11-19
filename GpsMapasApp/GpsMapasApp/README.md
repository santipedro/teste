# GPS e Mapas (Android, Java) com OpenStreetMap

App de exemplo para a aula de **GPS e Mapas**. Usa o **OpenStreetMap** (via biblioteca **osmdroid**), por isso
**não precisa de chave de API nem de Google Play Services**.

## O que o app faz

| Recurso | Como funciona |
|---|---|
| Mapa | osmdroid exibindo tiles do OpenStreetMap (com a atribuição obrigatória) |
| **Onde estou?** / **Acompanhar** | `LocationManager` (GPS + rede), círculo de precisão, permissão em tempo de execução |
| Tocar no mapa | adiciona um ponto; o endereço é descoberto pela geocodificação reversa do Nominatim |
| **Buscar** | endereço -> coordenadas com o Nominatim (uma consulta por clique, sem autocompletar) |
| Distâncias | Haversine, trecho a trecho e total em linha reta |
| **Salvar / Ver salvos** | grava os pontos em um banco **SQLite** do aparelho (tabela `locais`, igual à do PostgreSQL da aula) |

## Como abrir e executar

1. Instale o **Android Studio** (versão recente). O JDK já vem junto.
2. **File > Open** e escolha a pasta `GpsMapasApp`.
3. Aguarde o **Gradle Sync**. Na primeira vez ele baixa o Gradle 8.14.3 e as dependências (precisa de internet).
   O Android Studio também oferece instalar o **SDK Platform 36**: aceite.
4. Crie um emulador (**Device Manager**, API 24 ou superior) ou conecte um celular com depuração USB.
5. Clique em **Run** (triângulo verde).

> **Atualização do plugin do Android (AGP):** o projeto usa o AGP **8.13.2**. O Android Studio pode sugerir a
> versão 9.x. Ela é uma versão "major", com mudanças de API e comportamento: para a aula, **recuse** a
> atualização. Se aceitar, use o *Upgrade Assistant* do próprio Android Studio.

### GPS no emulador

O emulador não tem GPS real. Clique nos três pontinhos (**Extended controls**) > **Location**, escolha um ponto
no mapa (ou digite latitude e longitude) e clique em **Send**. Depois, no app, toque em **Onde estou?**.
No celular de verdade, ative a localização do aparelho e aceite a permissão quando o app pedir.

## Estrutura do projeto

```
app/src/main/java/br/edu/aula/gpsmapas/
  MainActivity.java     Tela única. Procure os comentários "PASSO 1" a "PASSO 8" (roteiro de estudo).
  GeoUtils.java         Haversine, validação e formatação (Java puro, testado).
  NominatimUrl.java     Monta as URLs do Nominatim (Java puro, testado).
  NominatimClient.java  Chamadas de rede em segundo plano, com intervalo de 1,1 s entre consultas.
  LocalDbHelper.java    Banco SQLite (SQLiteOpenHelper) com tabela "locais".
  PontoMapa.java        Modelo: nome, latitude, longitude.
app/src/main/res/       Layout (activity_main.xml), textos, cores e tema.
app/src/test/           Testes unitários (GeoUtilsTest) que rodam na JVM, sem emulador.
```

Para rodar os testes: botão direito em `GeoUtilsTest` > **Run**, ou `./gradlew test` no terminal.

## Regras de uso do OpenStreetMap (importante)

- **Tiles:** o servidor público é mantido por voluntários. Serve para estudo e uso leve. O osmdroid foi feito para respeitar os
  limites de conexões simultâneas da política, e o app **define o próprio User-Agent** (`config.setUserAgentValue(...)`). O User-Agent
  padrão do osmdroid é bloqueado pelo servidor.
- **Atribuição:** o mapa mostra "© OpenStreetMap contributors" (`CopyrightOverlay`). Não remova.
- **Nominatim:** máximo de **1 consulta por segundo**, sem autocompletar a cada tecla e com User-Agent que
  identifique o app. Em `NominatimClient.java` troque a constante `USER_AGENT` pelo nome do seu app e um contato real.
- Para uso intenso, hospede seu próprio servidor de tiles e de geocodificação ou contrate um provedor.

Políticas oficiais: https://operations.osmfoundation.org/policies/tiles/ e
https://operations.osmfoundation.org/policies/nominatim/

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Mapa cinza, sem imagens | Sem internet (no emulador também) ou tiles bloqueados. Veja o Logcat filtrando por `osmdroid`. |
| "Localização desligada" | Ative a localização do aparelho/emulador. Com permissão só "aproximada" o GPS não é usado. |
| "Permissão negada" | Configurações do app > Permissões > Localização. |
| "Falha na busca (HTTP 403/429)" | User-Agent genérico ou consultas rápidas demais. Veja `NominatimClient`. |
| Erro no Gradle Sync sobre JDK | Em **Settings > Build Tools > Gradle**, use o *Gradle JDK* embutido (JDK 17 ou superior). |

## Ideias de atividades

1. Mostrar no resumo a distância do ponto marcado até a posição atual (use `GeoUtils` ou `Location.distanceBetween`).
2. Permitir arrastar os marcadores e recalcular as distâncias.
3. Trocar o SQLite por um envio dos pontos a um servidor que grave no PostgreSQL (consulta parametrizada).
4. Desenhar um círculo de 1 km ao redor do ponto tocado (`Polygon.pointsAsCircle`).
5. Gravar o trajeto do modo "Acompanhar" e exibi-lo como linha, respeitando a privacidade do usuário (LGPD).

## O que foi verificado

Sem acesso ao repositório Maven do Google neste ambiente de preparo, o projeto **não foi compilado com o Gradle**.
Foram feitas as verificações que eram possíveis:

- XMLs bem formados e todas as referências a recursos (`R.id`, `R.string`, `@color` etc.) conferidas;
- **9 testes unitários executados** (distâncias conferidas com o PostgreSQL e com Python; vírgula decimal em celular em português);
- código Java compilado contra *stubs* das APIs do Android e do osmdroid (confere sintaxe e tipos, **não** a assinatura real das APIs);
- o SQL do banco executado no SQLite real (restrições `CHECK` e `NOT NULL`).

Se aparecer erro de compilação no primeiro **Build**, copie a mensagem exibida no painel *Build*: o ajuste costuma ser
de uma linha.
