# MVP — Rádio Embarcada

## Atualização autorizada — etapa 2

Para o ambiente de desenvolvimento, a fonte ativa agora é `LocalAssetMusicProvider`, que localiza MP3 recursivamente em `assets/music` (inclusive `music/MPB/`). A biblioteca é exclusivamente local, não versionada, e a programação funciona sem rede. Jamendo permanece disponível para integração futura, inativo em debug e release nesta fase. A biblioteca completa percorre ciclos aleatórios sem repetição antes de esgotar o ciclo, com buffer/preload próprios para assets. O workflow existente pode preparar a biblioteca privada durante o build usando MUSIC_ARCHIVE_URL; o aplicativo instalado continua totalmente offline. A separação MusicProvider → Programming → Player e MediaSession/MediaLibraryService permanece a mesma.

Para o teste privado/não comercial da etapa 2, a programação é montada localmente a partir de faixas individuais da API de tracks da Jamendo, sem emissora ou endpoint de rádio contínua. Essa instrução substitui, apenas nesta etapa, as referências abaixo ao stream final/programação exclusiva no servidor. O app mantém uma única estação sem gerenciamento de playlists pelo motorista.

A implementação separa MusicProvider, Programming/Queue e Player, usa ProgramItem extensível, buffer/preload/cache operacional e preserva MediaSession/MediaLibraryService. Nenhuma licença atual é presumida suficiente para a futura operação comercial. O motor definitivo, anúncios, vinhetas, jingles, backend e Android Auto completo continuam fora desta etapa. Configuração e roteiro de testes estão no README.


## Visão
Aplicativo Android multi-tenant para rádios 24/7 voltadas a plataformas locais de mobilidade.

Cada empresa usa a mesma base do app, mas com identidade visual e stream próprios.

## Objetivo do MVP
Validar:
1. streaming estável no Android;
2. reprodução em segundo plano;
3. configuração por cliente;
4. arquitetura compatível com Android Auto.

## Incluído
- Android
- Kotlin
- Jetpack Compose
- Media3 / ExoPlayer
- MediaSession
- MediaLibraryService
- stream HTTP/HTTPS
- play/pause
- status da transmissão
- notificação de mídia
- Audio Focus
- reconexão básica
- configuração mockada de tenant
- identidade visual por configuração
- persistência local básica
- estrutura para Android Auto

## Fora do MVP
- painel web
- programador automático definitivo
- servidor de streaming definitivo
- publicidade
- relatórios
- cobrança
- iOS
- Android Automotive OS

## Fluxo do motorista
1. Abrir o app.
2. Informar código da empresa.
3. App identifica o tenant.
4. Configuração é salva.
5. Tela da rádio é exibida.
6. Motorista toca Play.

## Tela principal
- logo do cliente
- nome da rádio
- AO VIVO
- play/pause
- conteúdo atual quando houver
- status de conexão

## Tenant de exemplo

```json
{
  "clientId": "alce",
  "activationCode": "ALCE2026",
  "companyName": "Alce App Transporte",
  "radioName": "Rádio Alce",
  "logoUrl": "https://exemplo.com/logo.png",
  "primaryColor": "#000000",
  "streamUrl": "https://radio.exemplo.com/alce",
  "enabled": true
}
```

## Programação
O app não cria playlists.

Arquitetura futura:

```text
Biblioteca musical
→ Motor de programação
→ Músicas + vinhetas + jingles
→ Encoder / servidor de streaming
→ URL da estação
→ App Android
→ Bluetooth / Android Auto / som do veículo
```

## Curadoria musical futura
Priorizar:
- músicas ambientes e democráticas
- sem baixo calão
- sem conteúdo sexual explícito
- sem agressividade excessiva
- sem graves exagerados
- sem som estridente
- sem grandes variações de volume

## Conteúdo comercial futuro
O motor poderá inserir:
- vinhetas
- jingles
- publicidade
- avisos
- campanhas por horário

## Requisitos do player
- tocar stream contínuo
- funcionar com tela apagada
- manter MediaSession
- mostrar notificação
- respeitar Audio Focus
- responder a controles externos
- reconectar automaticamente
- evitar consumo excessivo
- impedir múltiplas instâncias do player

## Android Auto
Preparar:
- MediaSession
- MediaLibraryService
- metadados compatíveis
- controles de mídia

Experiência desejada:

```text
Rádio do cliente
AO VIVO
[ Play / Pause ]
```

## Rede
Considerar:
- Wi-Fi
- 4G
- 5G
- troca de rede
- perda temporária de sinal

Usar retry com backoff.

## Persistência local
Salvar:
- tenant ativo
- configuração visual
- streamUrl
- preferências básicas

## Critérios de aceite
A primeira etapa está pronta quando:
1. projeto compila;
2. app abre;
3. stream de teste toca;
4. play/pause funciona;
5. áudio continua em segundo plano;
6. notificação funciona;
7. MediaSession funciona;
8. MediaLibraryService está configurado;
9. arquitetura permite Android Auto;
10. existe tenant mockado;
11. não existem credenciais hardcoded;
12. README explica como executar.

## Próximas fases
1. API real de tenants
2. servidor de programação
3. streaming 24/7
4. painel administrativo
5. Android Auto completo
6. campanhas e jingles
7. métricas e relatórios
8. licenciamento por cliente
