# Ferramentas e projetos encontrados

[Voltar ao projeto](../README.md)

## Captura, diagnóstico e interoperabilidade

| Ferramenta | Uso e evidência |
| --- | --- |
| Wireshark / TShark 4.6.10 | Decodificou HCI, AVDTP, AVRCP, RFCOMM e HFP. Os registros ETW usados no projeto foram convertidos para BTSnoop antes da análise. |
| Microsoft BTVS 1.14.0 | Capturou a reconexão e o anúncio `0100` via HFP. Em outro teste, o stream trouxe só tráfego anterior à consulta. Confira timestamps antes de atribuir uma resposta. |
| Windows ETW BTHPORT | Uma sessão nova e elevada cobriu o reteste RCSP e mostrou a consulta saindo em HCI. |
| Microsoft BTETLParse | Alternativa documentada para ETLs compatíveis com BTP. A conversão que produziu a evidência publicada neste projeto usou os eventos BTHPORT. |
| Histórico HCI do Android e ADB | Permitem comparar o log do SDK com o enlace do telefone. O extrator no workspace passou por verificações sintéticas. Falta um log real pareado com o ensaio RCSP. |
| Rust/WinRT e APIs Windows | Após vincular o alvo, serviram para consultar SDP, observar BLE e acessar o transporte serial. Uma consulta GATT de revisão depende de encontrar um periférico BLE atribuível ao fone. |

[BTVS](https://learn.microsoft.com/en-us/windows-hardware/drivers/bluetooth/testing-btp-tools-btvs) · [BTETLParse](https://learn.microsoft.com/en-us/windows-hardware/drivers/bluetooth/testing-btp-tools-btetlparse) · [HCI no Android](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging) · [Cliente GATT do Windows](https://learn.microsoft.com/en-us/windows/apps/develop/devices-sensors/gatt-client)

## Catálogo JieLi no Windows

Foram inventariados **27 pacotes** e **140 entradas de arquivos**. O pacote antigo da raiz continha 20 entradas. As versões abaixo são as registradas no catálogo mais novo. A presença ou a abertura de uma ferramenta é evidência distinta de uma resposta do Mali 2.

| Pacotes | Função e resultado |
| --- | --- |
| `ancdesigner` 0.1.38, `anctool` 0.1.0 | O ANC Designer recebeu V2.1.1 do Mali 2 pela COM5. O seletor ANC abriu variantes por SDK, sem leitura de coeficientes confirmada. |
| `eq` 3.1.8, `efftool` 0.1.58 | As interfaces de EQ e efeitos abriram. A leitura online depende do suporte do firmware. Nenhuma curva interna do Mali 2 foi obtida. |
| `sdktool` 2.0.59, `fwtools` 0.0.24 | Inspeção de arquivos FW/UFW. Três amostras AC693X foram reconhecidas e tiveram seus checksums lidos offline. O gerenciador deixa o campo de execução de `fwtools` vazio, pois há executáveis separados. |
| `ufwmaker` 1.1.14, `fwadd` 1.0.16 | Empacotamento UFW e anexação a FW. O teste de `ufwmaker` com uma amostra AC6936B falhou com `0xC0000005`. A amostra permaneceu íntegra. |
| `remotefmup` 0.0.7, `fw_upgrade` 1.4.50, `isddownload` 4.2.81, `flashwp` 1.0.6 | Atualização, bancada de gravação e conversão de parâmetros de proteção. Nenhum processo de atualização ou gravação foi validado no Mali 2. |
| `installer` 2.0.2, `pkgman2` 2.0.2, `luaconfig` 2.0.47 | Instalação e gerenciamento de pacotes, e parâmetros de projeto antes da compilação. O ambiente de desenvolvimento depende da família do SDK. |
| `3rd` 0.0.2, `sdkaudiocvt` 0.0.11, `post-build-tools` 0.0.4, `sdk_utils` 0.0.1 | Dependências e utilitários de conversão, recursos e build. O FFmpeg gerou e leu um WAV sintético. |
| `sdks-index` 1.0.0, `sdks-ac693x` 1.0.5, `ac697n-sdk-doc-1` 0.0.9, `ac697n-sdk-entry-1` 0.0.2, `ac700n-sdk-entry-1` 0.0.1, `dv20-sdk-20220513` 0.0.5 | Índices, modelos, PDFs e arquivos de outras famílias. As entradas AC697N e AC700N compartilham um caminho de projeto no catálogo auditado. O arquivo presente correspondia ao AC700N. |
| `fccassit` 1.1.3, `android-audio-tools` 0.1.0 | Auxílio em ensaios RF/FCC/BQB e ferramenta Android com manual. Nenhuma capacidade nova do Mali 2 foi confirmada por esses pacotes. |

[Documentação de ferramentas de firmware JieLi](https://doc.zh-jieli.com/Tools/zh-cn/firmware_tools/index.html)

## Projetos externos

| Projeto | Aplicação ao Mali 2 |
| --- | --- |
| [Kuba Control iOS](https://github.com/nms6yds2jb-create/Kuba_br_mali2/tree/6b47e34a8eb04266b1e9c2587f632e18baa05ed1) | Referência de interface e arquitetura. A auditoria detalha o que era protótipo ou simulação. |
| [BlueGauge](https://github.com/iKineticate/BlueGauge) | Referência Windows para exibir bateria Bluetooth na bandeja. |
| [spacebuds-ctl](https://github.com/Desmond123-arch/spacebuds-ctl) | Precedente de interoperabilidade RCSP com Oraimo Spacebuds Neo. Os dados e comandos daquele modelo exigiriam validação separada. |
| [H3 Control](https://github.com/tsnery/h3-control) | Exemplo de controle Windows para QCY H3. A investigação não estabeleceu compatibilidade de perfil ou hardware com o Mali 2. |
| [BluetoothAssistant](https://github.com/sgxgsx/BluetoothAssistant) | Referência de descoberta e pareamento Android. O código revisado não ofereceu leitura de firmware, captura HCI ou atribuição da bateria por lado. |

A comparação com o S81 ficou sem confirmação de chip ou protocolo em comum. Os projetos de exploração Bluetooth levantados durante a pesquisa ficaram fora da validação de interoperabilidade.
