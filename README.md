# Mali 2 Inspector Android 1.1 — APK Android nativo + SDK oficial JieLi

## Android 1.1

O APK de depuração fica em [Releases](https://github.com/soldieg/Kuba-Control-Android/releases). Requer Android 8 ou mais recente.

Depois de emparelhar, selecione o Mali 2 e toque em **Ler família do chip e protocolo ANC**. Essa ação usa RFCOMM no Android, com as duas consultas documentadas que responderam no Windows. A consulta usa o fone emparelhado selecionado no aplicativo. Em **Abrir demonstração de controles**, bateria, modos, toques e equalização são simulados localmente. O caminho existente pelo SDK oficial JieLi continua disponível para consultas RCSP com autenticação.

Veja [a auditoria do Kuba Control](AUDITORIA_KUBA_CONTROL.md) para a divisão de recursos entre Android e Windows.

A validação de transporte no celular exige o fone emparelhado e uma resposta registrada pelo aplicativo. A reprodução da captura Windows nos testes valida o decodificador.

O objetivo é avançar da identificação do serviço `JL_SPP` do **Kuba Mali 2** para um resultado binário: *SPP abre? SDK JieLi conecta? comando `requestDeviceInfo` recebe resposta?* E, após o primeiro comando aceito, buscar bateria, EQ, identificação, modos acústicos e configurações documentadas.

## Compilar o APK em Windows (rota local)

1. Instale **Android Studio** (JDK 17 integrado) e, em **SDK Manager**, instale **Android SDK Platform 35** e **Build Tools 35**. Aceite as licenças no Android Studio.
2. Extraia este ZIP em uma pasta normal. Não execute diretamente dentro do ZIP.
3. Abra **`COMPILAR_APK_WINDOWS.bat`**. O script confere o Android SDK, baixa **somente os dois AAR RCSP/autenticação** diretamente do repositório oficial da JieLi e verifica o SHA-1 de cada blob Git. Instala Gradle 8.9 se não existir. Não precisa Android Studio aberto.
4. A saída é `Mali2_Inspector_Android_v1_1_1_debug.apk` na raiz deste projeto. Assinado automaticamente com **chave DEBUG** da instalação do Gradle. Não serve para distribuição na Play Store.
5. Passe o APK para o celular e instale (autorize instalar app dessa fonte, se o Android solicitar), ou use `adb install -r Mali2_Inspector_Android_v1_1_1_debug.apk`.

Na primeira compilação, são necessárias conexões à internet para Gradle/Android, bibliotecas e dependências.

## Compilar no GitHub Actions (sem configurar SDK no Windows)

1. Crie um repositório **privado** no GitHub (evite enviar MACs e logs pessoais).
2. Suba **o conteúdo** desta pasta como raiz do repositório, incluindo `.github/workflows/build-apk.yml`.
3. Abra **Actions → Build Android APK debug → Run workflow**. Alternativamente o workflow inicia em `push` para `main`.
4. Ao concluir, abra o job e baixe o artefato `Mali2_Inspector_Android_debug_APK`, que contém `app-debug.apk` instalável.

**A compilação pela nuvem não foi realizada aqui**. Se o workflow falhar por mudança no SDK da JieLi, a saída completa do Gradle mostrará a classe/método que precisa ser adaptado.

## Permissões pedidas

| Permissão | Quando e por quê |
|---|---|
| `BLUETOOTH_CONNECT` | Android 12+: listar dispositivos já emparelhados, consultar serviços e conectar por SPP. Solicitada ao abrir o app. |
| `BLUETOOTH_SCAN` (`neverForLocation`) | Android 12+: o gerenciador oficial JieLi pode precisar executar descoberta durante a conexão. Pedida somente ao escolher **Conectar SDK JieLi**. |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (`maxSdkVersion=30`) | Compatibilidade Bluetooth Classic/SDK com Android 11 ou anterior. |

**Não são pedidas:** localização, acesso a armazenamento geral, internet, SMS, contatos, microfone, notificações, acesso root, depuração USB ou acessibilidade. O diagnóstico é local; `ACTION_CREATE_DOCUMENT` permite exportar um JSON sem permissões de armazenamento. O SDK de terceiros é baixado **na compilação**, não pelo aplicativo.

## Procedimento recomendado

1. Emparelhe o Mali 2 pelo Bluetooth nas **Configurações do Android**, antes de abrir o aplicativo.
2. Abra o app e aceite **Dispositivos próximos / Conectar**. Selecione explicitamente *Kuba Mali 2*; caso vários apareçam, confira o MAC do seu próprio fone.
3. Clique **Selecionar este fone e ler dados Android**. O app mostra os UUIDs conhecidos pelo Android.
4. Clique **Testar SPP puro — SOMENTE RECEBER**. Isso tenta abrir o RFCOMM seguro com o UUID SPP. Encerre o teste após alguns segundos. **Abrir e receber 0 bytes não comprova autenticação nem suporta RCSP.**
5. Clique **Conectar com SDK JieLi**. O app usa `BluetoothOption.PREFER_SPP`, `setUseDeviceAuth(true)` e `RCSPController.connectDevice(..., PROTOCOL_TYPE_SPP)`. O SDK gerencia sua própria negociação; não tenta burlar autenticação.
6. Quando o status indicar conexão pelo SDK, clique **Consultar RCSP**. Uma resposta válida a `requestDeviceInfo` habilita uma sequência limitada de consultas oficiais somente leitura. Sem essa resposta, o app **para** e registra o erro, em vez de insistir em comandos.
7. Para gestos, ative o monitor de áudio e marque os oito testes. Um registro de alteração de volume não demonstra ter capturado um quadro AVRCP; e o modo jogo não deve aparecer como ANC.
8. Use **Exportar relatório JSON** e envie o resultado, sem necessidade de enviar logs pessoais do telefone.

## Resultados esperados por frente

| Frente | Acesso e evidência |
|---|---|
| Emparelhamento e serviços | `getBondedDevices()` / `BluetoothDevice.getUuids()`, origem Android |
| Nível de bateria | Consulta RCSP e callback de bateria, conforme suporte do fone. |
| SPP | `createRfcommSocketToServiceRecord(SPP_UUID)` + `connect()`, erro ou socket aberto |
| RCSP e autenticação | SDK oficial, callback de conexão; **conexão por si só não prova autenticação** |
| Firmware e identificadores | `requestDeviceInfo()` e callback `DeviceInfo`; apenas valores realmente retornados |
| Bateria / volume / EQ internos | `getDevSysInfo()` com máscaras 0/1/4; callbacks reais `onBatteryChange`, `onVolumeChange`, `onEqChange` |
| ANC / transparência | `getCurrentVoiceMode()` e `getAllVoiceModes()`; confirmação por callback |
| Controles sensíveis e detecção | `getDeviceSettingsInfo()` → `ADVInfoResponse` se modelo suportar; gestos locais independentes |
| Modo jogo | Marcação manual e investigação de RCSP; `SysExpandFunc.isGameMode` existe no SDK genérico, mas não é prova de leitura disponível no Mali 2 |
| Codec negociado | **Não é extraído por este APK**; continua exigindo captura AVDTP/HCI válida |

## Segurança e limites

O APK não possui ações de gravar EQ, trocar ANC, atualizar firmware, renomear fone, alterar teclas ou mudar configurações. Os comandos RCSP enviados são leituras solicitadas pelo usuário. Use apenas uma sessão Bluetooth de diagnóstico por vez.

A biblioteca JieLi é genérica, não específica da Kuba. Uma resposta válida depende do firmware reconhecer e aceitar o protocolo e a autenticação. O app pode registrar **conexão SPP aberta, SDK sem resposta**, e esse é um resultado técnico útil: a próxima investigação deve mirar na configuração da autenticação/documentação OEM, não repetir varreduras BLE.

### Referências oficiais

- Repositório e bibliotecas: https://github.com/Jieli-Tech/Android-JL_Bluetooth
- Documentação de conexão Android: https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
- Permissões Bluetooth: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- SDK autenticado: https://doc.zh-jieli.com/Apps/Android/jielihome/en-us/master/development/import.html
- Consulta a dispositivos JieLi: https://github.com/Jieli-Tech/Android-JL_Bluetooth/blob/main/doc/JieLi_Home_SDK_V4.2.0_html_en/html/_sources/development/function/basic_func.rst.txt

## Para auditar o projeto

- `app/src/main/java/com/mali2/inspector/MainActivity.java` — interface, Bluetooth Android, socket SPP, gestos e relatórios.
- `app/src/main/java/com/mali2/inspector/JieliBridge.java` — transporte e consultas oficiais RCSP.
- `app/src/main/AndroidManifest.xml` — permissões; app sem INTERNET e sem OTA.
- `scripts/preparar_sdk_jieli.ps1` — download e hash dos dois AAR.
- `scripts/BUILD_WINDOWS.ps1` e `COMPILAR_APK_WINDOWS.bat` — build local.
- `.github/workflows/build-apk.yml` — build alternativo na nuvem.
