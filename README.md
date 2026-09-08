# WhisperBrain — Meu dia em temas

Versão **0.4.0-alpha**, em validação. Um mapa diário dos temas presentes nas notificações recebidas do WhatsApp, com mensagens de origem e caderno local.
Desenvolvido e compilado na nuvem para uso de quem só tem um telefone.

## Meu dia · mapa do WhatsApp

1. Ative a captura em **WhatsApp · novas mensagens** e conceda acesso às notificações no Android.
2. Aguarde uma notificação recebida e abra **Meu dia · mapa do WhatsApp**.
3. Escolha a data. As contagens mostram somente mensagens elegíveis daquele dia no fuso do telefone.
4. Configure uma chave da API OpenAI em **Configurar IA**.
5. Toque em **Analisar meu dia · IA** e confira o envio dos trechos selecionados.
6. Explore os temas no mapa ou na lista. Toque em um tema para conferir as mensagens de origem.

Cada análise propõe até 8 temas e 8 ligações entre temas. Uma conversa pode aparecer em vários temas;
um tema pode reunir mensagens de conversas diferentes. Resumos e motivos são interpretações da IA,
não comprovação de leitura, respostas, decisões ou participação do usuário.
O protótipo analisa somente mensagens recebidas pelas notificações: Gmail, Slack e Discord não estão conectados.

A seleção alterna mensagens recentes de cada conversa salva, até 120 mensagens e 60.000 caracteres de mensagens
serializadas. Cada texto tem até 1.400 caracteres; nomes e horários também entram no pedido.
A tela informa seleção, total disponível e textos abreviados. Essa amostra pode deixar temas de fora.
Saudações e anexos sem texto podem não gerar temas. Notas modificadas pelo usuário ficam fora da análise,
para não serem tratadas como texto original recebido.

A API só é chamada após o comando de análise e a confirmação de envio. Usa o modelo de texto configurado,
inicialmente `gpt-4.1-mini`, saída JSON estruturada, `store:false`, sem ferramentas nem ações externas.
O app valida que os IDs citados pertencem à amostra e que ligações têm evidências de ambos os temas.
Essa validação não garante a correção semântica da resposta: confira os trechos citados.
O pedido tem timeout de 90 segundos e não é repetido automaticamente.
Cancelar interrompe a espera, mas a API pode já ter processado e cobrado o pedido.

O mapa fica criptografado no telefone e pode ser reaberto offline. Mensagens novas sinalizam que a análise
está desatualizada; não provocam outro gasto de API. Editar ou apagar uma mensagem usada na análise remove
os mapas derivados dela. Uma resposta atrasada não restaura mapas de mensagens editadas ou apagadas.
**Excluir análise deste dia** remove somente o mapa; as notificações permanecem no caderno.

O esquema SQLite migra de 2 para 3 sem apagar sessões, notas ou ligações.
O backup inclui mapas no JSON e um Markdown por dia em `days/`. Na importação, as referências são reescritas
para os novos IDs. Se já existir um mapa da mesma data e fuso, ele é preservado.
Um mapa importado é sinalizado para atualização, pois o conjunto local de mensagens pode ter mudado.
Backups das versões anteriores continuam aceitos. As versões antigas do app não restauram os mapas diários.

## O modelo do caderno

| Elemento | Comportamento |
| --- | --- |
| Sessão | Conversa com data/hora de início, nome do evento e estado aberta/encerrada |
| Neurônio | Nota, transcrição, áudio, resumo ou recomendação; mantém sua origem |
| Sinapse | Ligação entre dois neurônios, com relação, motivo e estado proposta/aceita |
| Grafo da conversa | Neurônios e conexões daquela sessão |
| Grafo completo | Conexões entre sessões; busca por nota ou evento |
| Histórico | Notas persistem localmente após fechar e reabrir o app |

## Usar sem microfone

1. Abra **Nova sessão** e escreva o nome do evento.
2. Digite uma nota e toque em **Salvar neurônio**.
3. Continue escrevendo offline. Não é necessária uma chave nem permissão de microfone.
4. Para apoio da IA, configure sua chave e toque em **Gerar sinapses com IA**.
5. Abra uma sinapse proposta para aceitar ou remover. A IA não transforma hipóteses em fatos aprovados.

O texto é salvo como anotação local. Somente os comandos de IA enviam contexto à API.
A análise recebe a nota foco, até 14 notas recentes da mesma conversa e, se habilitado,
até 10 notas de outras sessões selecionadas por palavras em comum. Não há envio integral automático do caderno.
As respostas usam a API Responses com `store:false` e formato JSON estruturado; isso não elimina a retenção de segurança do provedor.
Modelo de texto editável, inicialmente `gpt-4.1-mini`.

## WhatsApp pelas notificações

1. Abra **WhatsApp · novas mensagens** e toque em **Ativar captura**.
2. Na tela do Android, conceda acesso às notificações para **WhisperBrain · WhatsApp**.
3. Volte ao app e confira **Capturando novas mensagens**.
4. Receba uma nova mensagem com o WhatsApp fora da conversa aberta. Abra **Ver conversas salvas**.
5. Use **Pausar captura** quando quiser; as notas já salvas continuam no caderno.

A captura do WhatsApp pessoal vem selecionada. WhatsApp Business é opcional e começa desligado.
O Android concede acesso amplo às notificações; o código descarta imediatamente outros pacotes.
Somente o perfil Android onde o WhisperBrain está instalado é considerado.
Não há leitura do banco privado, busca de notificações antigas, respostas automáticas ou chamadas à IA durante a captura.
As APIs de IA continuam sendo acionadas pelo usuário.

Cada conversa identificada pela notificação ganha uma sessão por dia, com data, remetente e origem `notification`.
Uma ligação **capturada depois** registra a sequência de captura da mesma conversa, inclusive entre dias.
Isso não afirma relação causal ou concordância. As ligações sugeridas pela IA continuam propostas para revisão.
A mensagem original pode ser incompleta; a interface não a apresenta como uma anotação escrita pelo usuário.

Mensagens anteriores à ativação e mensagens da pausa são descartadas pelos horários disponíveis na notificação.
Repetições são deduplicadas em transação SQLite, inclusive depois de fechar o app ou excluir uma nota.
A identidade de conversa usa o identificador do atalho Android quando disponível, com a chave da notificação e o título como alternativa.
Quando não há identificador, mudanças no nome da conversa podem abrir outra sessão.
Nomes de contatos iguais não são usados como chave de união. Identificadores e impressões de deduplicação usam HMAC com segredo local criptografado.
O banco da versão 0.2 migra para a versão 2 do esquema preservando as notas, ligações e áudios existentes.

Limites: conteúdo oculto, falta de notificações, desconexões ou notificações em formato diferente podem gerar lacunas.
Respostas enviadas pelo usuário geralmente não são capturadas. Resumos agrupados, chamadas e entradas históricas são ignorados.
Fotos e áudios não são baixados: somente o texto ou indicação de anexo disponibilizado na notificação pode virar nota.
O fallback de notificação simples usa o horário da notificação, que pode ser menos preciso que o horário da mensagem.
O diagnóstico registra estado e contadores, sem nomes, mensagens ou chaves.

Se o Android bloquear **Configuração restrita**, veja [a orientação oficial do Android](https://support.google.com/android/answer/12623953?hl=pt-BR).
O app oferece um atalho para suas informações e deixa a concessão da permissão sob controle do usuário.

## Áudio e escuta ao vivo

- **Gravar áudio local:** notas de até 3 minutos, guardadas criptografadas; a gravação termina ao sair da tela.
- **Transcrever áudio com IA:** comando explícito dentro de uma nota de áudio; cria uma transcrição ligada ao original.
- **Escuta ao vivo:** mantém o microfone em um serviço Android iniciado pelo usuário, com notificação e Stop.
- Resumos e dicas ao vivo são salvos na sessão local escolhida.
- **Salvar transcrições:** habilita a transcrição da API, com custo adicional. Texto reconhecido pode conter erros.
- **Guardar também o áudio local:** opcional; ocupa aproximadamente 2,9 MB/minuto. Até 45 minutos por escuta.
- Dicas faladas usam uma voz offline Android, com saída privada verificada e volume reduzido.
- Análises ocorrem em pausas ou a cada 20 segundos de fala contínua detectada; a voz aguarda uma pausa.
- **Analisar agora** solicita a análise sem depender do detector de pausa.

A entrada enviada à IA tem breves lacunas durante as dicas faladas, para evitar eco. A gravação local opcional mantém o áudio do microfone.
O fim abrupto de uma escuta pode impedir a chegada das últimas transcrições; o arquivo de áudio só aparece depois de finalizado.
A transcrição de um arquivo já salvo aceita até 24 MB nesta versão. Para gravações ao vivo mais longas, habilite a transcrição durante a escuta.
O áudio de entrada vem do microfone: não há captura de chamadas telefônicas, WhatsApp ou Teams.
Não há identificação confiável de falantes. A voz é fala suave de TTS, não uma voz neural com estilo de sussurro.

## Grafo e revisão

As notas do usuário aparecem em verde. Recomendações e resumos da IA aparecem em âmbar.
Linhas tracejadas indicam propostas da IA; linhas contínuas indicam ligações aceitas ou criadas pelo usuário.
Toque em um neurônio ou sinapse, arraste o grafo e amplie com dois dedos. A lista de notas oferece outra forma de navegação.
O desenho mostra até 180 neurônios recentes que correspondem ao filtro; os demais permanecem salvos e pesquisáveis.
As conexões propostas só podem apontar para IDs de notas efetivamente enviados à IA. IDs inventados e auto-ligações são descartados.

## Armazenamento e backup

O SQLite guarda o conteúdo de sessões, notas e conexões com AES-GCM e chave do Android Keystore.
IDs, relações de chave estrangeira e datas usados pelos índices continuam como metadados locais.
Os áudios são arquivos criptografados; cópias temporárias legíveis são usadas na gravação/reprodução e removidas ao concluir.
O backup automático Android está desativado.

**Exportar caderno e áudios** cria um ZIP legível, escolhido pelo usuário, com JSON, notas Markdown, links `[[id]]` e arquivos WAV/M4A.
O ZIP não contém a chave da API. **Importar um backup** recria IDs e conexões sem sobrescrever sessões existentes.
O limite de importação é 500 MB, 5.000 sessões, 30.000 neurônios e 100.000 sinapses por arquivo.
Guarde o backup em um lugar que você controla. Apagar o app também apaga seus dados internos e a chave do Keystore.

## Compilar pelo telefone

Abra [Actions](https://github.com/edwardmonteiro/whisperbrain-android/actions), escolha a última execução bem-sucedida e baixe **WhisperBrain-Android-APK**.
Extraia o ZIP e abra `app-debug.apk`. Uma nova alteração do código inicia outra compilação.
Os APKs desse artefato usam assinatura de depuração do runner; a assinatura pode mudar entre execuções.
A entrega direta `WhisperBrain-v0.4.0-alpha.apk` usa uma assinatura pessoal estável, identificada em `VALIDATION.json`.
A chave privada foi guardada separadamente; ela não faz parte deste repositório nem dos artefatos públicos.
A entrega direta 0.4 usa a mesma assinatura das entregas diretas 0.2 e 0.3; instale sobre a versão existente para preservar o caderno.
A passagem dos APKs 0.1.0/0.1.1 para essa entrega exige desinstalar a versão antiga, apagando seus dados internos.
Tenha sua chave da API e memórias importantes disponíveis antes disso. Veja [o guia de instalação](PHONE-SETUP.md).

Android 12+; compile/target 36; Java 17; Gradle 8.13; AGP 8.11.1; SQLite nativo; OkHttp 4.12.0.
A chave da API é inserida somente no app. A compilação e os testes não precisam dela e não fazem chamadas pagas.

## Validação

A validação do APK 0.4 será registrada em `VALIDATION.json` após a execução do workflow.
O workflow compila, executa regras de intervenção, testes JVM, lint e testes Android 16 em emulador.
Os testes de mapas usam notificações e respostas de IA sintéticas; não precisam de uma conta real do WhatsApp
nem de chamadas pagas à API. Os cenários incluem datas/fusos, seleção limitada, origem das evidências,
migração, persistência criptografada, exclusão, backup/importação e renderização das telas.
A instalação e as notificações reais no Samsung do usuário exigem confirmação no telefone.

## Referências oficiais

- [OpenAI Responses e saída estruturada](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Realtime](https://developers.openai.com/api/docs/guides/realtime-conversations)
- [Transcrição de arquivos](https://developers.openai.com/api/docs/guides/speech-to-text)
- [Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [AndroidX MessagingStyle](https://developer.android.com/reference/androidx/core/app/NotificationCompat.MessagingStyle)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android microphone foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)
- [Emulador Android no GitHub Actions](https://github.com/ReactiveCircus/android-emulator-runner)
