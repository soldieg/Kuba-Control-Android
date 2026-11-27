# Ensaios e evidências

[Voltar ao projeto](../README.md)

Resumo dos ensaios de 26–27/11/2026. Os números abaixo descrevem o material registrado em cada janela. Os arquivos brutos ficaram no acervo local porque incluem endereços e tráfego de dispositivos próximos.

## Windows

| Ensaio | Resultado e interpretação |
| --- | --- |
| SDP sem cache | O Windows abriu a consulta e confirmou `JL_SPP` no canal 1 e `JL_HFP` no canal 4. As versões de perfil foram 1.2 e 1.9. |
| ANC Designer e diagnóstico serial | O ANC Designer 0.1.38 recebeu **V2.1.1** na COM5. Uma leitura de identificação separada retornou `0x07` (**JL710N**). Nenhum modo acústico foi lido ou alterado nesse ensaio. |
| Captura guiada de 26/11 | Foram atribuídos **462 pacotes ACL** ao enlace com o fone. O áudio negociou AAC. Os controles Play/Pause vieram pelo AVRCP. Durante Normal → Transparência → ANC → Normal, foram observados pacotes AVRCP e AVDTP sem campo atribuível ao modo. |
| Lado direito no estojo | A captura registrou bateria HFP `9/9` durante a janela, sem indicar lado, bateria do estojo ou estado de encaixe. A retirada ocorreu após o fim da captura. |
| Reconexão por BTVS em 27/11 | O quadro **3092** vinculou o endereço do fone ao handle HCI `0x0081`. Dos **2.882 pacotes ACL** atribuídos, o quadro **3249** contém a identificação XAPL com `0100`; os quadros **3253** e **3256** registram bateria `9/9`. O ETL do dia anterior contém o mesmo XAPL. |
| BLE com abertura e fechamento | O anúncio mais forte da janela de abertura chegou a −58 dBm, sem nome, e já aparecia no HCI antes do sinal para abrir. Os **110 quadros HCI** de anúncio do ensaio ficaram sem atribuição segura ao Mali 2. |
| BLE em modo de pareamento | O Windows contou **51 anúncios** nas três janelas. O HCI teve **38 quadros de anúncio** após a confirmação do pareamento, sem candidato do Mali 2. A resolução WinRT do endereço Classic como BLE também falhou; por isso a leitura de `0x2A26` não chegou a ser executada. |
| RCSP `GetTargetInfo` | A primeira tentativa retornou zero bytes. O PCAPNG do BTVS continha apenas tráfego anterior à consulta. No reteste com ETW novo e elevado, o quadro **291** continha os **14 bytes** em ACL de saída pelo RFCOMM canal 1. Após cerca de 12 s, os quadros **1420/1423** mostraram o fechamento do canal e a confirmação de transporte. Não houve resposta de aplicação, nem versão para comparar. |
| RCSP `Get System Info` | Uma consulta de leitura do modo acústico foi aceita pela API Windows. A janela de 12 s terminou com zero bytes. Esse ensaio usou uma operação distinta de `GetTargetInfo`. |

O HCI permite demonstrar que o host entregou dados ao controlador local. Para atribuir tráfego, o ensaio também precisa vincular horário, direção, handle e canal à sessão do fone. Eventos de transporte e respostas de aplicação têm funções diferentes.

## Android

| Ensaio | Resultado |
| --- | --- |
| APK 1.1.0 | O empacotamento deixou classes AndroidX necessárias ao SDK fora do APK. Foram observados erros envolvendo `ActivityCompat` e `ArrayMap`. |
| APKs 1.1.1 e 1.1.5 | A inspeção DEX dos APKs de release confirmou a presença das classes requeridas. Os quatro testes Java do checkout 1.1.5 passaram. |
| Diagnóstico de identidade | A consulta separada confirmou `0x07` e `V2.1.1` no próprio fone. |
| SDK RCSP no 1.1.5 | O transporte SPP chegou a `READY`. O SDK registrou timeout em `GetTargetInfo` (`0x03`) e a sessão terminou sem versão, modo ANC ou bateria por peça. Sem HCI Android da mesma janela, a entrega da consulta e a origem da desconexão ficaram abertas. |
| Anúncios em JSON | O teste mais recente analisado guardou **27 anúncios** em uma escuta marcada como interrompida. Nenhum pôde ser atribuído ao Mali 2. O app pede anúncios legados, e a opção `neverForLocation` pode alterar o que é entregue ao scanner. Um teste pareado com HCI é necessário para avaliar essas hipóteses. |

## Como registrar um resultado novo

Registre plataforma, versão da ferramenta, identificação da unidade, horário com fuso, operação, resposta e ponto da captura. Preserve a saída original localmente. Indique explicitamente se o valor veio do fone, do sistema operacional, de uma simulação ou da documentação de outro modelo.

Uma leitura de firmware conclusiva precisa de um campo de versão atribuído à unidade, ou de documentação oficial que relacione a versão à revisão e ao lote do aparelho. [JieLi, JL710N e firmware](jieli.md)
