# HUD, 데이터 소스, 외부 이미지

## HUD

게임 화면(인게임)에 항상 그려지는 XML 문서. 입력은 받지 않는 읽기 전용이다. 데모: `hud_demo.xml`.

```toml
# config/better-uiux/config.toml
[hud]
ids = "hud_demo,my_hud"      # config/better-uiux/ui/<id>.xml (없으면 기본 제공). 비워 두면 HUD 없음
```

- 루트는 `<hud>` 를 권장한다(`<screen>` 도 된다). 요소 배치는 `anchor`/`margin`/row/column 으로 한다.
- 파일을 저장하면 바로 다시 불러온다(핫리로드). 틀린 XML 이면 이전 모양을 유지하고 로그에 경고를 남긴다.
- F1(HUD 숨기기)이면 그려지지 않는다.
- Fabric 은 `HudElementRegistry`, NeoForge 는 GUI 레이어(`RegisterGuiLayersEvent`)로 연결한다.

## 데이터 소스(읽기 전용 바인딩)

어떤 문서에서든 `text="HP {player.health}/{player.maxHealth}"`, `<progress value="{player.food}" max="20"/>` 처럼 쓴다.
값은 문서가 그려질 때마다 읽고, 플레이어/월드가 없으면(타이틀 화면 등) 빈 값이다.

| 이름 | 값 |
|---|---|
| `fps` | 프레임 수 |
| `player.health` `player.maxHealth` | 체력(정수면 정수, 아니면 소수 한 자리) |
| `player.food` `player.saturation` | 허기, 포화도 |
| `player.air` `player.maxAir` | 산소 |
| `player.armor` | 방어력 |
| `player.xpLevel` `player.xpProgress` | 경험치 레벨, 다음 레벨까지 진행률(0~100) |
| `player.slot` | 선택한 핫바 칸(1~9) |
| `player.x` `player.y` `player.z` | 내림한 좌표 |
| `player.yaw` `player.pitch` | 보는 방향(각도) |
| `player.name` `player.gamemode` | 이름, 게임 모드 |
| `world.time` `world.day` `world.dimension` | 하루 안의 시각(0~23999), 날짜, 차원 id |

모드는 자기 값을 추가한다: `UiApi.registerDataSource("mymod.coins", () -> coins)` (이름은 `[A-Za-z0-9_.-]`).
쓰기는 지원하지 않는다 — 값을 바꾸는 일은 등록된 액션이나 스크립트만 한다.

## 외부 이미지

```xml
<image src="file:demo.png"/>                       <!-- 크기를 안 정하면 이미지 원래 크기 -->
<image src="file:spin.gif" width="24" height="24"/> <!-- GIF 는 저절로 움직인다 -->
```

- 파일 위치: `config/better-uiux/images/<이름>` (없으면 기본 제공 `assets/better_uiux/images/<이름>`). 폴더 밖 경로(`..`)는 거부한다.
- 형식: png, jpg, bmp, gif(애니메이션 포함). WebP/APNG 는 아직 지원하지 않는다.
- 불러오는 동안은 그 자리가 비어 있다가, 준비되면 문서가 원래 크기로 다시 배치된다.
- 상한: 파일 8MB, 한 변 4096px, GIF 프레임 256개, (가로×세로×프레임) 16M 픽셀. 넘으면 로그에 사유를 남기고 그리지 않는다.
- 캐시: 최대 32개, 전체 32M 픽셀-프레임. 넘으면 오래 안 쓴 것부터 텍스처를 닫는다. 파일을 고치면(수정 시각) 다시 불러온다.
- 백그라운드에서 해독하고, 텍스처는 공식 `DynamicTexture` 경로로만 올리며, GIF 는 프레임이 바뀔 때만 한 번 올린다.

데모: `/uiux open images`.

## 영상

```xml
<video src="file:demo.mp4"/>                          <!-- 크기를 안 정하면 영상 원래 크기 -->
<video src="file:demo.mp4" width="64" height="36"/>
```

- 형식: **H.264 mp4**(`.mp4` `.m4v` `.mov`). 순수 Java(JCodec)로 해독하므로 네이티브 라이브러리가 필요 없다. **소리는 지원하지 않는다.** WebM/VP9, H.265 는 지원하지 않는다.
- 파일 위치는 이미지와 같다: `config/better-uiux/images/<이름>`(없으면 기본 제공). 폴더 밖 경로는 거부한다.
- 계속 반복(loop) 재생한다. 해독은 영상마다 백그라운드 스레드에서 하고, 화면에는 재생 시각에 맞는 가장 최근 프레임만 올린다(해독이 재생보다 빨리 가지 않는다).
- 몇 초 동안 그려지지 않으면 해독이 멈추고, 다시 그려지면 처음부터 재생한다.
- 상한: 동시에 최대 3개 재생, 해독 크기는 가장 긴 변 960px 이하로 줄인다(원본이 크면 정수 배율로 축소), 파일 128MB.
- 해독이 재생 속도를 못 따라가면 영상이 느려질 수 있다(프레임을 건너뛰지는 않는다). 작은 크기의 영상을 권장한다.

데모: `/uiux open video`.
