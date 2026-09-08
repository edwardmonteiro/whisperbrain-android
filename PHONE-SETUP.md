# WhisperBrain 0.4 — primeiro uso no telefone

## Instalar a entrega direta 0.4.0

Baixe `WhisperBrain-v0.4.0-alpha.apk` pela conversa e abra o arquivo no telefone.
Se o Android solicitar, permita a instalação para o aplicativo que abriu o APK.

**Se você instalou uma entrega direta 0.2 ou 0.3 desta conversa, instale a 0.4 por cima. Não desinstale.**
A assinatura pessoal é a mesma e o banco de dados é atualizado preservando suas sessões.

**A passagem de 0.1.0/0.1.1 para esta entrega exige desinstalar o APK antigo**, pois a assinatura anterior era temporária.
Antes disso, tenha sua chave da API disponível e copie qualquer memória importante: a desinstalação apaga os dados e configurações internos.
A partir desta entrega direta, uma chave pessoal de assinatura estável permite futuras atualizações compatíveis sem apagar o caderno.
Isso vale para APKs assinados com a mesma chave; os artefatos de depuração do GitHub Actions ainda têm assinatura temporária.

## Analisar os temas do dia

1. Ative a captura do WhatsApp conforme os passos abaixo.
2. Abra **Meu dia · mapa do WhatsApp**, na tela inicial.
3. Escolha a data e confira o número de mensagens recebidas.
4. Em **Configurar IA**, insira sua chave da API OpenAI. Use uma chave de API, não sua senha do ChatGPT.
5. Toque em **Analisar meu dia · IA** e confirme o envio dos trechos, nomes e horários selecionados.
6. Toque em um tema para ver o resumo e as mensagens de origem. Você pode ampliar e arrastar o mapa.

A captura é local e não tem chamadas à IA. Gerar temas usa internet e créditos da API, separados do ChatGPT.
Abrir um mapa já salvo funciona offline. Mensagens novas não refazem a análise automaticamente.
O limite por análise é 120 mensagens, com trechos de até 1.400 caracteres e um orçamento total de texto.
Confira a cobertura exibida: a amostra pode ser menor que todas as mensagens recebidas.
O mapa mostra interpretações das notificações recebidas; não identifica suas respostas dentro do WhatsApp.

Se o dia estiver vazio, confira a captura e teste uma mensagem que gere uma notificação visível.
Se a API recusar o pedido, confira chave, saldo e modelo de texto. A análise aguarda até 90 segundos.
Cancelar não garante o cancelamento da cobrança de um pedido já recebido pela API.

## Ativar novas mensagens do WhatsApp

1. Abra **WhatsApp · novas mensagens** e toque em **Ativar captura**.
2. Habilite **WhisperBrain · WhatsApp** no acesso às notificações do Android.
3. Volte e aguarde **Capturando novas mensagens**.
4. Receba uma mensagem com o WhatsApp fora da conversa aberta.
5. Toque em **Ver conversas salvas**. A sessão usa o nome da conversa e a data.

O botão **Pausar captura** interrompe a entrada. Ao retomar, mensagens da pausa ficam fora.
WhatsApp Business tem uma opção própria, inicialmente desligada. Capturar mensagens não usa sua chave de IA nem o microfone.
Áudios e fotos não são recebidos como arquivos; o app guarda apenas o conteúdo disponibilizado na notificação.
Para recomendações, abra uma nota e use **Gerar sinapses deste neurônio**.

Se aparecer **Configuração restrita**, use a ajuda dentro do app. Quando disponível:
Informações do app → menu ⋮ → Permitir configurações restritas. Depois volte à permissão de notificações.
Caso a captura continue vazia, use **Copiar diagnóstico do WhatsApp**. Ele não inclui conteúdo das conversas.
O Android e o WhatsApp precisam disponibilizar o texto na notificação; notificações ocultas ou ausentes não geram uma conversa completa.

## Caderno por texto

1. Abra **+ Nova sessão**.
2. Dê um nome ao evento, por exemplo **Planejamento da semana**. A data é automática.
3. Escreva no campo de texto e toque em **Salvar neurônio**.
4. Feche e reabra o app: a sessão continua na lista, organizada por data.

Esse fluxo funciona offline, sem chave e sem permissão de microfone.

## Recomendações e sinapses

1. Abra **Configurar IA e voz** e insira sua chave da API.
2. Volte à sessão e selecione **Relacionar outras conversas**, se desejar esse contexto.
3. Digite uma pergunta e toque em **Gerar sinapses com IA**, ou use esse comando em uma nota já salva.
4. Abra as ligações propostas para **Aceitar** ou **Remover ligação**.
5. Explore **Grafo desta conversa** ou **Grafo de todas as conversas**.

Os comandos da IA usam internet e créditos de API, cobrados separadamente do ChatGPT.

## Áudio

- **Gravar áudio local** guarda uma nota de até 3 minutos no telefone.
- Abra esse neurônio para ouvir em saída privada ou **Transcrever áudio com IA**.
- **Escuta ao vivo** abre o modo de acompanhamento em tempo real.
- Escolha se quer transcrições e se quer guardar também o arquivo de áudio.
- Toque em **Iniciar escuta**, aguarde **Ouvindo** e teste uma frase.
- Pause por 3 segundos ou use **Analisar agora**. Resumos e dicas serão adicionados à sessão.
- O comando **Parar escuta** encerra o microfone; a sessão do caderno pode continuar por texto.

## Proteger seu histórico ao atualizar

Use **Exportar caderno e áudios** e guarde o ZIP antes de qualquer desinstalação.
O ZIP contém texto, áudios e mapas diários legíveis; não contém a chave da API. A importação preserva as sessões existentes. Se já houver mapa da mesma data e fuso, ele é preservado.
A assinatura do APK deve ser a mesma para atualizar sem desinstalar. Consulte a orientação que acompanha o APK entregue.

## Se algo falhar

Na escuta ao vivo, use **Copiar diagnóstico**. O texto não contém chave, conversa nem memórias.
No caderno, informe a ação e a mensagem exibida. Suas notas locais não dependem de a API responder.
Ainda precisamos confirmar microfone, Bluetooth, auricular e uso com tela bloqueada no seu aparelho.
