# Rádio Embarcada — etapa 2

Rádio Alce com **faixas musicais individuais** consultadas na API `/v3.0/tracks/` da Jamendo. O aplicativo monta a sequência e escolhe a próxima faixa. **Não utiliza emissora, endpoint de rádio contínua ou fallback de rádio ao vivo.** Teste privado/não comercial; as licenças e os termos atuais não são autorização para o produto comercial futuro.

## Abrir e executar

- Android Studio Meerkat 2024.3.1 ou superior, compatível com AGP 8.9.2.
- JDK completo 17; SDK Platform 35 e Build Tools 35.0.0.
- Android 8.0/API 26 ou superior, com internet para obter catálogo e conteúdo inicial.
- Gradle 8.11.1 incluído no wrapper.

Abra a raiz no Android Studio, sincronize, configure o provedor conforme abaixo e execute o módulo `app`. A identidade Alce é local em `app/src/main/assets/tenant.json`. Não há login, escolha de gêneros, gerenciamento de músicas, anúncios ou backend nesta etapa.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug --warning-mode all
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No Windows, use `gradlew.bat`. Configure `JAVA_HOME` para o JDK e `ANDROID_HOME` para o SDK, ou `sdk.dir` no `local.properties`.

## APK como artefato

O workflow **Android debug** (`.github/workflows/android-debug.yml`) executa testes, build e lint a cada push em `main` ou por execução manual. Após sucesso, o APK fica disponível em **GitHub → Actions → Android debug → execução → Artifacts → radio-alce-debug**, por sete dias. O APK não entra no histórico Git.

O build do Actions não recebe credenciais da Jamendo: abre normalmente, mas a reprodução pelo provedor exige um APK compilado com sua configuração local descrita abaixo. Para esse teste, gere `app/build/outputs/apk/debug/app-debug.apk` no seu ambiente configurado.

MP3 de testes, incluindo `app/src/main/assets/music/*.mp3`, e arquivos RAR, incluindo `MPB.rar`, são ignorados pelo Git e permanecem apenas no ambiente local. Não são enviados ao GitHub nem ficam disponíveis no runner do Actions. Configurações locais, caches, assinaturas privadas e saídas de build também são ignorados.

## Configurar a Jamendo

Obtenha seu próprio **client_id** registrando uma aplicação no [portal da Jamendo](https://devportal.jamendo.com/). Nenhuma chave de demonstração é fornecida ou inventada.

No **`local.properties`**, ignorado pelo Git, acrescente a propriedade a seguir, substituindo o marcador pelo ID da sua aplicação:

```properties
jamendo.clientId=<SEU_CLIENT_ID>
```

Alternativamente, configure a variável de ambiente **`JAMENDO_CLIENT_ID`** antes de iniciar o Android Studio/Gradle. A variável não vazia tem precedência sobre o arquivo local. Depois sincronize, recompile e reinstale o APK. Não coloque o valor em `gradle.properties`, assets versionados ou Kotlin.

O Gradle gera `BuildConfig.JAMENDO_CLIENT_ID` dentro de `build/`, que não é versionado. **Um valor distribuído no APK pode ser extraído**; esta configuração local não é cofre de secrets. Não use tokens privados, senhas ou credenciais de produção. Não publique um APK contendo seu ID sem avaliar as condições do provedor.

Sem client_id, o projeto compila e abre. Ao tocar Play, informa que a fonte musical não está configurada, sem chamar a API e sem tocar conteúdo alternativo. Um ID inválido é tratado como erro de configuração. Neste ambiente não havia client_id; os testes de integração de dados usam fixtures, sem consultas reais autenticadas.

## Catálogo e licenças

`network/jamendo/JamendoConfiguration.kt` centraliza endpoint, perfil e timeouts. São três consultas em paralelo com **fuzzytags** (OR, favorecendo resultados que combinam tags), unidas e deduplicadas:

- `synthwave+synthpop+newwave+retro`
- `rock+pop+softrock+alternative`
- `electronic+retro+instrumental`

As formas `newwave` e `softrock` evitam ambiguidades nos parâmetros de múltiplas tags. A codificação de listas segue os exemplos oficiais (`+` no URL).

Outros parâmetros: `speed=low+medium`, `durationbetween=120_480`, `groupby=artist_id`, `type=single+albumtrack`, `order=relevance`, `boost=popularity_month`, `include=licenses+musicinfo`, `audioformat=mp31` (MP3 96 kbps). Cada consulta solicita 30 candidatos, com paginação rotativa. Se uma consulta falhar, as demais podem fornecer o catálogo.

O adaptador aceita somente áudio HTTPS com título/artista, duração válida, licença Creative Commons reconhecida e `audiodownload_allowed=true`, adotando um critério conservador para as cópias do cache operacional. Usa a URL **`audio`**, nunca `audiodownload`. Descarta gêneros/tags como metal, hard rock, hardcore, agressivo e explícito quando informados pelo catálogo. Não é uma garantia automática de curadoria: ouça e avalie as faixas antes de qualquer uso fora do teste.

Título e artista permanecem na tela/MediaSession; capa e duração são apresentadas quando disponíveis. A tela credita Jamendo e oferece links diretos para a faixa e para sua licença. Não há crossfade nem alteração do conteúdo musical nesta etapa. Consulte os [termos da API](https://devportal.jamendo.com/api_terms_of_use) e a licença individual. O uso comercial futuro exige avaliação/licenciamento próprio.

## Arquitetura

```text
MusicProvider → AutomaticProgramming / QueueBuilder → ProgramItem → ProgramPlayer → MediaSession → UI
```

| Parte | Responsabilidade |
| --- | --- |
| `data/music/MusicProvider` | Contrato assíncrono que fornece `Track`; independe do player e da Jamendo. |
| `network/jamendo` | HTTP, DTOs, parsing e conversão das respostas; mensagens de erro sanitizadas, sem URLs/client_id nos logs da aplicação. |
| `model` | `Track`, `ProgramItem`, tipos de conteúdo, `NowPlaying` e estados. |
| `programming` | Perfil de tamanho/histórico/paginação e ordem pseudoaleatória. |
| `player/ProgramPlayer` | ExoPlayer único, fontes progressivas, buffer, preload, cache e metadados; sem regras de catálogo. |
| `player/RadioService` | MediaLibraryService/MediaLibrarySession, comandos externos, ciclo de vida e coordenação assíncrona da programação. |
| `ui` | Compose/ViewModel; apenas comandos ao MediaController e representação dos estados/metadados. |
| `storage` / `data` | Configuração local da identidade do tenant e repositório. |

`RadioApplication` conecta a implementação Jamendo ao contrato. Para trocar o catálogo, implemente `MusicProvider.fetchTracks(limit, offset)`, devolva modelos internos com URLs individuais e direitos apropriados e substitua a implementação nessa composição. A fila, o serviço e a UI continuam usando os mesmos contratos. O player atual suporta áudio progressivo; um catálogo com outro protocolo pode exigir o módulo correspondente do Media3.

Os tipos `MUSIC`, `STATION_ID`, `JINGLE`, `ADVERTISEMENT` e `ANNOUNCEMENT` já podem ser representados por `ProgramItem`. Apenas `MUSIC` é produzido atualmente. Não há implementação de vinhetas, jingles, anúncios ou motor comercial. A representação genérica também permite introduzir uma estratégia de transição/crossfade futura sem colocar regras de catálogo no player.

## Fila automática

`ProgrammingConfiguration.kt` define lotes de **20 faixas**, histórico recente de **60 IDs** e reposição quando restarem **até três próximas faixas**. O catálogo é embaralhado, sem IDs duplicados no lote, repetição imediata de faixa ou artista consecutivo, inclusive na fronteira entre lotes. IDs pendentes não são novamente enfileirados. Se o catálogo for pequeno, a fila pode ficar menor que 20; as regras de adjacência não são quebradas para completar o lote.

As faixas anteriores são removidas da timeline/gerenciador após a transição, preservando posição e buffer da faixa atual. Conteúdo passado pode reaparecer em lotes futuros quando necessário, mas não imediatamente. ExoPlayer faz a transição natural entre as fontes, sem outro player tocando em paralelo. A UI e a biblioteca de mídia apresentam uma única estação, sem expor gerenciamento de playlists.

## Buffer, preload e cache

Tudo está centralizado em **`player/PlaybackConfiguration.kt`**:

| Configuração | Valor |
| --- | --- |
| Mínimo do DefaultLoadControl | 30.000 ms |
| Retomar abastecimento do buffer estável | abaixo de 45.000 ms |
| Meta do buffer estável | 60.000 ms |
| Teto do DefaultLoadControl | 90.000 ms |
| Iniciar primeira reprodução | 1.500 ms |
| Retomar após rebuffer | 3.000 ms |
| Próxima faixa: preload | 45.000 ms |
| Segunda próxima: preload | 15.000 ms |
| Intervalo de avaliação de carga progressiva | 64 KiB |
| Cache operacional LRU | 200 MiB (209.715.200 bytes) |
| Idade do cache para descarte na próxima inicialização | 24 horas |

`RadioLoadControl` delega os critérios de início/rebuffer/teto ao **DefaultLoadControl** e acrescenta histerese de 45–60 s. Os valores são metas: o conteúdo, bitrate, blocos de extração e rede podem causar pequena variação; não se promete exatamente 60 segundos offline.

O **DefaultPreloadManager do Media3 1.6.1** compartilha allocator, configuração, looper e fontes com o único ExoPlayer. Após o buffer atual atingir 30 s, prioriza a próxima faixa e depois a segunda. `getMediaSource` fornece ao player a mesma fonte pré-carregada. Preload é desativado/retomado conforme pausa, rede e buffer, sem eliminar áudio já preparado. Não se baixa antecipadamente a fila inteira.

**SimpleCache + LeastRecentlyUsedCacheEvictor**, no `cacheDir` privado, é compartilhado pelo player/preload. O índice usa `StandaloneDatabaseProvider`; os dados antigos são removidos por LRU, o Android pode limpar o cache e uma inicialização após 24 h descarta o cache anterior. Não há botão de download, biblioteca permanente, persistência do catálogo ou modo offline. Uma sessão pode consumir os trechos já preparados; uma inicialização nova continua exigindo catálogo/rede. O cache é apenas o necessário à operação, conforme a cláusula de caching dos termos da API.

## Rede, estados e serviço

A perda de rede **não pausa, reinicia, recria o player ou limpa sua timeline**. Enquanto houver áudio preparado, mantém `Ao vivo` e mostra que está tocando conteúdo preparado sem internet. Ao esgotar, mostra `Sem conexão`; ao recuperar carregamento, `Reconectando`. Os demais estados são `Carregando` e `Pausado`.

Falhas transitórias de carregamento usam retry de **1, 2, 4, 8, 16 e até 32 segundos**. `RadioLoadErrorPolicy` evita um erro terminal apenas porque acabou o número padrão de tentativas, permitindo consumir o buffer existente enquanto a rede volta. HTTP permanentemente inválido e erros de formato seguem recuperação limitada da faixa; após três falhas terminais, avança para a próxima quando existir. Catálogo também usa backoff; não há consulta por frame nem loop agressivo. Retorno da rede pode aguardar o próximo retry agendado.

Wi-Fi/dados móveis usam a rede padrão do Android. A mesma instância ExoPlayer, a sessão e as fontes permanecem durante a troca. Audio Focus, chamadas, fone desconectado, Bluetooth e wake mode continuam a cargo das APIs padrão Android/Media3. Pause/Stop cancelam a intenção de reproduzir e consultas pendentes; Play retoma a posição, sem saltar para o início.

O manifest mantém `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. MediaLibraryService segue com `foregroundServiceType=mediaPlayback` e ações MediaLibraryService/MediaBrowserService. AndroidX adiciona sua permissão interna de assinatura para receptores dinâmicos. Não há novas permissões de usuário. A notificação é a padrão Media3 ligada à MediaSession. O serviço exportado resolve apenas a estação conhecida, sem aceitar URLs externas nem gerenciamento externo da fila. Raiz/estação e metadados da música atual preservam a base Android Auto, sem integração automotiva completa nesta etapa.

## Teste manual

1. Configure um client_id válido, recompile/reinstale e abra Rádio Alce. Toque Play: deve consultar o catálogo, carregar áudio e mostrar título/artista/capa/duração.
2. Aguarde a rede abastecer o buffer. Com uma conexão suficientemente rápida, deixe tocar por **30–60 segundos** antes de desligar a internet; desligar imediatamente após Play não mede o buffer estabilizado.
3. Desative **Wi-Fi e dados móveis** e cronometre. Deve seguir audível com o aviso de conteúdo preparado. O objetivo é superar significativamente os ~5 s anteriores; a duração efetiva varia conforme o que conseguiu carregar e a proximidade do fim da faixa.
4. Reative a rede **antes** de acabar o áudio preparado. Confira que não volta ao início da música nem troca a sessão. Também teste manter a rede desligada até esgotar e depois recuperar.
5. Repita em Wi-Fi → 4G/5G e 4G/5G → Wi-Fi. Confira continuação da posição/metadados, sem duplicação de áudio. Valide próximas faixas com a rede desligada perto da transição; somente os trechos realmente pré-carregados são garantidos.
6. Deixe passar mais de 20 faixas, verificando reposição automática, diversidade de artistas e ausência de repetição imediata. Os controles de próxima faixa do sistema podem acelerar parte do teste de transição.
7. Teste Pause/Play na UI, notificação e Bluetooth; pause durante a consulta inicial e durante ausência de rede. Não deve iniciar sozinho após uma pausa voluntária. Rotacione/reabra a tela e confirme que há somente uma reprodução.
8. Repita em segundo plano, tela apagada e removendo a tarefa dos recentes enquanto toca. Teste chamadas, outro aplicativo de áudio e remoção do fone. Force-stop interrompe o aplicativo por definição do Android.
9. Sem client_id, confira a mensagem de configuração ausente; não deve tocar uma emissora externa. Com ID inválido, confira erro sanitizado.

Os testes JVM usam fixtures/fakes e cobrem conversão/licenças, fila, fronteiras de repetição, ProgramItem, fornecedor sem configuração, estados e buffer. Não substituem testes audíveis, de bateria/fabricante, chamada, notificação e redes em dispositivo. Sem client_id válido não é possível confirmar seleção real, disponibilidade das URLs nem sonoridade das faixas.

## Dependências desta etapa

Mantidas Kotlin/Compose Compiler 2.1.20, Compose BOM 2025.04.01/Material 3, Activity 1.10.1, Lifecycle 2.9.0 e Media3 1.6.1. Declarados Media3 datasource/database para cache, coroutines-android 1.10.2 e Coil Compose 2.7.0 para capas. JUnit 4.13.2 e JSON 20240303 apenas para testes. Sem framework de backend, downloader, banco remoto ou framework de injeção.

## Validação da entrega

### Correção de inicialização — 0.2.1

Corrigido o fechamento ao abrir causado por `IllegalStateException: getBackBufferDurationUs not implemented` na construção do ExoPlayer. A delegação Kotlin `LoadControl by delegate` não encaminha os métodos `default` da interface Java. `RadioLoadControl` agora encaminha explicitamente os métodos atuais de ciclo de vida, seleção de faixas, back buffer, início e preload ao DefaultLoadControl, preservando a configuração de buffer.

`RadioLoadControlTest` cobre construção/back buffer, preparação/liberação e seleção/início com buffer pequeno. Os três testes falham na implementação anterior e passam com a correção. Nenhuma dependência ou permissão foi acrescentada. A versão do APK é 0.2.1 (`versionCode=2`).

Validação da correção: **28 testes aprovados**, `assembleDebug` e `lintDebug` com **BUILD SUCCESSFUL**, lint com os mesmos **0 erros/10 avisos** e assinatura do APK verificada. APK: `app/build/outputs/apk/debug/radio-alce-0.2.1-debug.apk`.

A tentativa de validação no emulador Android 15 sem aceleração foi inconclusiva: o próprio sistema sofreu watchdog/reinicialização e `DeadSystemException` antes da execução do aplicativo. A abertura precisa ser confirmada no aparelho ou em um emulador com aceleração; não foi apresentada como teste de runtime aprovado.

Para investigar um fechamento em um dispositivo específico, conecte-o com depuração USB, limpe o Logcat, abra o app e capture o buffer de crash:

```sh
adb logcat -c
adb shell am start -n br.com.radioembarcada/.ui.MainActivity
adb logcat -b crash -d
```

Não compartilhe credenciais ou URLs autenticadas eventualmente presentes em logs de terceiros.

Build executado com JDK 17/SDK 35: **BUILD SUCCESSFUL**. **28 testes aprovados**, sem falhas ou erros. Além dos testes anteriores, há cobertura de fila/ProgramItem, fronteiras de artista/faixa, conversão/licenças do provedor, parâmetros de tags, ausência de client_id, estados com buffer offline, histerese/configuração de buffer, ciclo de vida do LoadControl e invalidação de consultas pendentes após Pause/novo Play.

A retomada assíncrona também tem uma proteção específica para o comportamento do Media3 1.6.1, que chama Play após falha/cancelamento de `onPlaybackResumption`. Resultados substituídos são invalidados, e esse Play de fallback é ignorado. Não há emissora alternativa quando o catálogo falha.

Lint: **0 erros e 10 avisos revisados**, mantidos visíveis: oito sugestões de atualização de dependências fixadas no conjunto SDK 35/Media3 1.6.1; serviço exportado para controladores de mídia; configuração HTTP herdada da etapa 1 (a integração Jamendo aceita somente HTTPS). Nenhum erro de compilação ou lint foi suprimido. Relatórios: `app/build/reports/`.

Manifest mesclado, ações/tipo do serviço, Audio Focus, MediaSession e comandos da biblioteca foram conferidos. A busca de padrões e inspeção dos arquivos destinados ao Git, incluindo documentos do ZIP inicial, não encontrou credenciais. Configuração local e fontes geradas permanecem ignoradas.

**Não havia client_id nem dispositivo/emulador Android disponível nesta execução.** Portanto, consultas autenticadas, seleção/sonoridade real, tolerância offline medida, notificação audível, chamadas e transições Wi-Fi/dados ainda dependem do roteiro manual com sua configuração. As estruturas de segundo plano/Auto da primeira etapa foram preservadas; a validação em dispositivo deve ser repetida para esta nova fonte de conteúdo.

## Arquivos da etapa 2

- Novos modelos: `model/Track.kt`, `model/ProgramItem.kt`, `model/NowPlaying.kt`.
- Novo contrato/adaptador: `data/music/MusicProvider.kt` e `network/jamendo/{JamendoConfiguration,JamendoMusicProvider,JamendoResponseParser,JamendoTrackDto,JamendoTrackMapper}.kt`.
- Nova programação: `programming/{ProgrammingConfiguration,QueueBuilder,AutomaticProgramming}.kt`.
- Nova infraestrutura de reprodução: `player/{ProgramPlayer,PlaybackConfiguration,RadioLoadControl,RadioLoadErrorPolicy,PlaybackIntent}.kt`.
- Alterados: `RadioApplication.kt`, `model/{Tenant,ConnectionState}.kt`, `storage/LocalTenantStore.kt`, `player/{RadioService,RadioController}.kt`, `ui/{MainActivity,RadioViewModel}.kt`, `assets/tenant.json`, `app/build.gradle.kts`, `README.md` e `docs/MVP.md`.
- Novos testes/fixture: `QueueBuilderTest`, `JamendoResponseTest`, `JamendoConfigurationTest`, `PlaybackConfigurationTest`, `PlaybackIntentTest` e `jamendo-tracks.json`; atualizado `ConnectionStateTest`. Mantido `RetryPolicyTest`.

Caminhos Kotlin relativos a `app/src/main/java/br/com/radioembarcada/`; testes em `app/src/test/`.
