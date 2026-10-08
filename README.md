# Rádio Embarcada — desenvolvimento com assets locais

Rádio Alce com **faixas musicais individuais**. Nesta fase, em debug e release, `LocalAssetMusicProvider` busca MP3 recursivamente em `app/src/main/assets/music`, incluindo `music/MPB/` e outras subpastas. O aplicativo monta a sequência e escolhe a próxima faixa. Jamendo permanece implementado para uso futuro, porém inativo em todas as variantes atuais. **Não utiliza emissora, endpoint de rádio contínua ou fallback de rádio ao vivo.**

## Desenvolvimento local

Mantenha os MP3 de testes em `app/src/main/assets/music/MPB/` ou qualquer subpasta de `music/`. Nenhum nome de arquivo ou quantidade é fixado no código. Recompile e reinstale após adicionar/remover arquivos: o provider acessa os assets **do APK instalado**, não a pasta do computador em tempo de execução.

O catálogo é lido em `Dispatchers.IO` e mantido em memória durante a sessão. A busca aceita `.mp3` e `.MP3`, ignora outros arquivos e usa o caminho completo como ID estável. Título, artista e duração são lidos das tags pelo MediaMetadataRetriever; sem título válido, usa o nome completo do arquivo sem a extensão. Tags defeituosas não interrompem a construção do catálogo; artista ausente pode ser inferido do padrão `Artista - Título.mp3`. Artista ausente é mostrado como “Rádio”; cada arquivo sem artista recebe identidade própria para não reduzir toda a fila a uma única faixa. A capa embutida é transmitida à MediaSession/UI quando disponível e até 256 KiB; imagens maiores são omitidas para limitar memória e mensagens IPC. Não se atribui licença Creative Commons nem capa fictícia à biblioteca local.

O catálogo completo alimenta lotes de até 20 `ProgramItem(MUSIC)`: todas as faixas são selecionadas uma vez por ciclo, antes de uma nova ordem aleatória. Faixas ainda pendentes não são duplicadas; faixas com erro de reprodução são excluídas da sessão. A preferência por artistas diferentes não impede a continuidade quando só resta um artista. A prevenção de artistas consecutivos usa tags/nome quando o artista é conhecido. Assets usam `asset:///music/...` com caracteres escapados, são lidos diretamente pelo DefaultDataSource e não são duplicados no cache HTTP. MP3 são empacotados sem compressão adicional para leitura de metadados/seek. Não há dependências ou permissões novas.

Debug funciona sem internet e sem client_id: carregamento inicial, reposição e preload consideram a disponibilidade do provider local. Media3, MediaSession, MediaLibraryService, notificação, foco de áudio, segundo plano e estrutura Android Auto permanecem. Uma biblioteca vazia mostra “Programação indisponível.”, sem crash ou fallback para Jamendo; o Logcat de debug informa a contagem zero. Offline não produz aviso de internet nem estado “Sem conexão” para esta fonte. Jamendo continua usando cache, buffer, filtros e reconexão descritos abaixo quando selecionado.

Para testar: gere o APK no ambiente que contém os arquivos, abra Rádio Alce, toque Play, confira os metadados, passe várias faixas, pause/retome e teste modo avião, segundo plano, tela apagada e controles da notificação. Para validar reposição, avance além do primeiro lote de 20 faixas. Com fonte local, modo avião verifica independência da rede; não mede tolerância do streaming Jamendo.

MP3 e `MPB.rar` permanecem ignorados, incluindo subpastas e extensões maiúsculas. Não são apagados nem incluídos nos commits. **O APK gerado no ambiente com as músicas contém esses arquivos**; mantenha esse APK somente no teste privado autorizado e não o publique como biblioteca musical. Não há presunção de licença comercial.

## Abrir e executar

- Android Studio Meerkat 2024.3.1 ou superior, compatível com AGP 8.9.2.
- JDK completo 17; SDK Platform 35 e Build Tools 35.0.0.
- Android 8.0/API 26 ou superior; a fonte local funciona sem internet após instalar o APK com os MP3.
- Gradle 8.11.1 incluído no wrapper.

Abra a raiz no Android Studio, sincronize, confira os assets locais e execute o módulo `app` em debug. A identidade Alce é local em `app/src/main/assets/tenant.json`. Não há login, escolha de gêneros, gerenciamento de músicas, anúncios ou backend nesta etapa.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug --warning-mode all
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No Windows, use `gradlew.bat`. Configure `JAVA_HOME` para o JDK e `ANDROID_HOME` para o SDK, ou `sdk.dir` no `local.properties`.

## GitHub Actions — ambiente oficial de validação e APK

O workflow existente **Android debug** (`.github/workflows/android-debug.yml`) roda em Ubuntu com **Temurin JDK 17**, Android SDK **Platform 35 / Build Tools 35.0.0**, Gradle Wrapper **8.11.1** validado e cache de Gradle. Não usa o SDK do Codespaces. Executa preparação opcional da biblioteca, testes do preparador, `testDebugUnitTest`, `lintDebug` e `assembleDebug`, nessa ordem. Uma falha impede o upload; o APK é conferido, inclusive sua assinatura, antes de publicar o Artifact.

A cada push em `main`, ou em **GitHub → Actions → Android debug → Run workflow → main**, o resultado bem-sucedido disponibiliza **Artifacts → radio-embarcada-debug-com-musicas**, contendo **app-debug.apk**, por sete dias. O caminho de build é `app/build/outputs/apk/debug/app-debug.apk`. O APK não é versionado.

### Biblioteca privada no Actions — APK de teste com músicas

O secret de repositório **MUSIC_ARCHIVE_URL** contém uma URL HTTPS assinada temporária para `MPB.rar`. O runner Ubuntu instala `unrar`, baixa o arquivo em diretório temporário com timeout de até 30 minutos e valida que não está vazio. O log informa apenas detecção do secret, tamanho do RAR e contagem de músicas; nunca imprime a URL. RAR4/RAR5 e ZIP sem senha são aceitos, preservando subpastas.

Após a extração, os MP3 são copiados para `app/src/main/assets/music/`. Zero arquivos provoca falha **antes do build** com “Nenhum MP3 foi encontrado após a extração da biblioteca de testes.” Não há quantidade fixa de 77 no código. Este workflow destinado ao APK com músicas exige o secret; erro HTTP falha imediatamente. Se a URL expirar, atualize o secret em **Settings → Secrets and variables → Actions → MUSIC_ARCHIVE_URL**, sem escrever seu valor no repositório, e execute novamente.

São executados `./gradlew test testDebugUnitTest`, lint e assembleDebug. Ao final, o APK é inspecionado como ZIP: todos os caminhos MP3 de `assets/music/` devem corresponder à biblioteca antes do build e estar não vazios. O log mostra a contagem incorporada e o tamanho do APK. O Artifact **radio-embarcada-debug-com-musicas** contém `app-debug.apk`, com retenção de sete dias e sem compressão extra do Artifact. O APK grande é esperado; nenhum áudio é convertido ou reduzido.

O download ocorre somente durante o job: **o aplicativo instalado não usa R2, a URL, autenticação ou internet**. A URL não é incorporada ao APK. A biblioteca e o RAR não entram no Git, e músicas locais existentes não são apagadas nem sobrescritas. O Artifact com biblioteca destina-se exclusivamente aos testes privados autorizados.

Sem biblioteca no computador, o projeto ainda compila e abre normalmente, informando “Programação indisponível.” ao pressionar Play. **Biblioteca não presente neste workspace; o APK precisa ser compilado em um ambiente que contenha os MP3 em assets/music.** O Actions pode preparar esse ambiente com o secret já configurado.

### Diagnóstico de desenvolvimento

Somente builds DEBUG registram a tag `RadioDiagnostics`: provider ativo, quantidade encontrada, faixa atual/próxima, arquivos ignorados e erros de reprodução. Consulte com `adb logcat -s RadioDiagnostics`. A UI permanece uma rádio simples; não mostra termos de arquivos, playlists ou provider. Ausência de metadados usa o nome do arquivo; áudio corrompido é registrado, excluído e pulado na mesma sessão/player.

## Configurar a Jamendo para uso futuro

A composição está em `RadioApplication.musicProvider`: todas as variantes atuais usam assets; Jamendo está preservado e inativo. Para testar Jamendo em debug futuramente, substitua somente a seleção nessa composição e configure o client_id; a fila e o player não dependem do catálogo. As instruções e o roteiro de streaming abaixo se aplicam à fonte Jamendo.

Obtenha seu próprio **client_id** registrando uma aplicação no [portal da Jamendo](https://devportal.jamendo.com/). Nenhuma chave de demonstração é fornecida ou inventada.

No **`local.properties`**, ignorado pelo Git, acrescente a propriedade a seguir, substituindo o marcador pelo ID da sua aplicação:

```properties
jamendo.clientId=<SEU_CLIENT_ID>
```

Alternativamente, configure a variável de ambiente **`JAMENDO_CLIENT_ID`** antes de iniciar o Android Studio/Gradle. A variável não vazia tem precedência sobre o arquivo local. Depois sincronize, recompile e reinstale o APK. Não coloque o valor em `gradle.properties`, assets versionados ou Kotlin.

O Gradle gera `BuildConfig.JAMENDO_CLIENT_ID` dentro de `build/`, que não é versionado. **Um valor distribuído no APK pode ser extraído**; esta configuração local não é cofre de secrets. Não use tokens privados, senhas ou credenciais de produção. Não publique um APK contendo seu ID sem avaliar as condições do provedor.

Quando Jamendo estiver selecionado, sem client_id o projeto compila e abre; Play informa configuração ausente, sem chamar a API e sem tocar conteúdo alternativo. Um ID inválido é tratado como erro de configuração. Os testes de integração de dados usam fixtures, sem consultas reais autenticadas.

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

`RadioApplication` conecta exclusivamente a fonte local ao contrato nesta fase. Para trocar o catálogo, implemente `MusicProvider.fetchTracks(limit, offset)`, declare `requiresNetwork=false` se for uma fonte local, devolva modelos internos com URLs individuais e direitos apropriados e substitua a implementação nessa composição. A fila, o serviço e a UI continuam usando os mesmos contratos. O player atual suporta áudio progressivo; um catálogo com outro protocolo pode exigir o módulo correspondente do Media3.

Os tipos `MUSIC`, `STATION_ID`, `JINGLE`, `ADVERTISEMENT` e `ANNOUNCEMENT` já podem ser representados por `ProgramItem`. Apenas `MUSIC` é produzido atualmente. Não há implementação de vinhetas, jingles, anúncios ou motor comercial. A representação genérica também permite introduzir uma estratégia de transição/crossfade futura sem colocar regras de catálogo no player.

## Fila automática

`ProgrammingConfiguration.kt` define lotes de **20 faixas**, histórico recente de **60 IDs** e reposição quando restarem **até três próximas faixas**. O catálogo é embaralhado, sem IDs duplicados no lote, repetição imediata de faixa ou artista consecutivo, inclusive na fronteira entre lotes. IDs pendentes não são novamente enfileirados. Se o catálogo for pequeno, a fila pode ficar menor que 20; as regras de adjacência não são quebradas para completar o lote.

As faixas anteriores são removidas da timeline/gerenciador após a transição, preservando posição e buffer da faixa atual. Conteúdo passado pode reaparecer em lotes futuros quando necessário, mas não imediatamente. ExoPlayer faz a transição natural entre as fontes, sem outro player tocando em paralelo. A UI e a biblioteca de mídia apresentam uma única estação, sem expor gerenciamento de playlists.

## Buffer, preload e cache

A fonte local usa LoadControl com **mínimo 1 s / máximo 5 s / início 250 ms / rebuffer 500 ms**, sem a histerese remota. O preload oficial prepara **3 s da próxima faixa e 1 s da segunda**, sem esperar 30 s do buffer atual. Usa wake mode local, DefaultDataSource/AssetDataSource e nenhum cache de áudio duplicado. As configurações remotas abaixo permanecem preservadas para o futuro provedor de rede.


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

## Teste manual de streaming Jamendo (futuro)

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

O workflow é a fonte oficial dos resultados: consulte os passos e o resumo da execução no Actions. O resumo informa quantidade de testes Android, erros/avisos de lint e MP3 realmente presentes no APK. Os testes não dependem da biblioteca privada de 77 músicas.

A suíte atual contém **48 testes JVM por variante** (provider/recursão/subpastas/metadados/fallback/biblioteca vazia, fila/repetição/ProgramItem, conversão Jamendo, estados, buffer e política de erros) e **9 testes Python** do preparador (biblioteca opcional, ZIP, RAR4/RAR5 com mocks, caminhos inválidos, credencial sanitizada e preservação local). Para repetir a validação:

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew testDebugUnitTest lintDebug assembleDebug --warning-mode all --max-workers=2
```

Os avisos de lint existentes são revisados e permanecem visíveis: sugestões de atualização de dependências, serviço exportado para controladores e configuração HTTP herdada e target API 35. Não se suprimem erros para liberar o build. Os relatórios locais ficam em `app/build/reports/`. MediaSession/MediaLibraryService, foco de áudio, metadados e arquitetura multi-tenant foram preservados; testes de áudio, chamadas e Android Auto precisam de dispositivo.

Arquivos locais inexistentes/inacessíveis e formatos defeituosos são pulados e excluídos da sessão, sem retry infinito; falhas transitórias de rede mantêm o backoff e o áudio preparado. O player/sessão não são recriados entre faixas. O encaminhamento explícito de métodos do LoadControl, que corrige o fechamento da versão anterior ao abrir, continua coberto pelos testes.
