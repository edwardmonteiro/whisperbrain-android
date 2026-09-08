# WhisperBrain no seu telefone — 0.1.1-alpha

## Instalar ou atualizar

1. Abra [Actions](https://github.com/edwardmonteiro/whisperbrain-android/actions) no navegador do telefone.
2. Selecione a última execução bem-sucedida de **Build WhisperBrain APK**.
3. Baixe o artefato **WhisperBrain-Android-APK**, extraia o ZIP e abra `app-debug.apk`.

A assinatura de teste pode mudar entre compilações. Se o Android recusar a atualização por conflito de assinatura,
a versão anterior precisará ser desinstalada. **Desinstalar apaga a chave salva, as configurações e as memórias.**
Antes disso, guarde as notas que quiser manter e tenha sua chave disponível para inserir novamente no app.

## Teste de uma frase

1. Abra **Testar áudio privado** e confira se ouve a voz pelo fone ou auricular.
2. Em **Configurações de conexão e voz**, insira a chave da API, selecione **Português (Brasil)** e salve.
3. Toque em **Iniciar escuta**. Aguarde **Ouvindo**; o botão deve mudar para **Parar escuta**.
4. Diga: “Tenho uma reunião amanhã e preciso organizar minhas prioridades.”
5. Pause por 3 segundos ou toque em **Analisar agora**.
6. Veja o resumo perto do topo e o contador **respostas**. A voz aguarda uma pausa.
7. Toque em **Parar escuta** para encerrar.

Com as dicas automáticas ativadas, uma fala contínua detectada também recebe análise a cada 20 segundos,
sem depender da primeira pausa. Quando a IA não sugerir uma nova dica, o app mostra que a análise terminou.
Ruído pode afetar a detecção; a análise manual também funciona sem um evento de pausa.

## Se continuar sem resposta

Toque em **Copiar diagnóstico** e cole o texto na conversa de suporte.
O texto contém status e contadores; não inclui chave, conteúdo da conversa, objetivo ou memórias.

| Indicação | O que verificar |
| --- | --- |
| Ouvindo não aparece | Leia a mensagem de conexão/voz; o microfone só inicia após a preparação. |
| Barra não se mexe | Acesso ao microfone no Android, permissão do app e outro app usando o microfone. |
| Áudio enviado cresce, falas detectadas fica em zero | A API ainda não detectou voz. Fale perto do microfone e use Analisar agora. |
| Falas detectadas cresce, respostas fica em zero | Use Analisar agora e copie o diagnóstico se a análise não terminar. |
| Respostas cresce, mas não há voz | Veja o resumo e a dica; a IA pode escolher silêncio. Teste o áudio privado separadamente. |
| Sem saldo / acesso negado | Confira chave, acesso ao modelo e faturamento da API OpenAI. |

A API é cobrada separadamente do ChatGPT. Use conversas de teste com a concordância dos participantes.
O app capta áudio do microfone; não implementa gravação de chamadas.
A versão continua experimental: testes locais de protocolo não comprovam conexão com a API real nem áudio no telefone.
