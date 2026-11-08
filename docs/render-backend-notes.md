# 렌더 백엔드 조사 노트 (2026-11-08 기준)

> 웹 검색 기반 1차 조사. 일부는 2차 자료(커뮤니티 이슈/블로그)라 **구현 착수 시 공식 소스/디컴파일로 재검증**할 것.

## 1. 버전 현황
| 항목 | 내용 |
|---|---|
| 버전 체계 | `26.x` (26.1부터 **완전 비난독화** → 매핑 불필요) |
| 최신 안정판 | **26.3** (Vulkan은 opt-in) |
| Vulkan 기본화 | **26.4 스냅샷부터** ("Default" = "Prefer Vulkan"), 26.4-snapshot-7(2026-11-03)까지 확인 |
| 26.2 | Vulkan 실험적 도입, "OpenGL은 Vulkan 안정화 후 제거 예정" (Fabric 공지) |
| Java | 최소 **Java 25** (이 PC: JDK 25.0.4 설치됨) |
| 리소스팩/데이터팩 포맷 | 98.0 / 122.0 (26.4 snapshot 1) |

## 2. 로더 지원
| 로더 | 26.3 | 26.4(스냅샷) |
|---|---|---|
| Fabric | Loader 0.20.1(stable), Fabric API `0.165.2+26.3` | Fabric API `0.165.4+26.4` 존재 |
| NeoForge | `26.3.0.101-beta` (베타) | **없음** (26.4용 NeoForge 아직 미출시) |

- 빌드 도구(문서 근거): Fabric = Loom 플러그인 id **`net.fabricmc.fabric-loom`**, mappings 줄 제거. NeoForge = **ModDevGradle** (`net.neoforged.moddev`, 최신 2.0.148 확인). 정확한 Loom 버전은 Fabric Develop 사이트에서 착수 시 확인(maven 메타 조회로는 확정 못 함).
- 26.1 이하 모드는 26.1+에서 컴파일 의존으로도 사용 불가 → 의존 라이브러리도 26.x 대응판 필요.

## 3. 렌더 API 변화 (2차 자료)
- 원시 OpenGL 호출 모드는 **Blaze3D API로 이주 필요**. (우리 원칙 "GL 직접 호출 금지"와 일치)
- `RenderPipeline`: uniform/sampler 직접 대신 `BindGroupLayout` 리스트, 다중 `ColorTargetState`/`VertexFormat`.
- `GuiGraphics.blit` 은 **첫 인자로 `RenderPipeline`**, 텍스처 좌표 인자 타입 변경.
- GUI 렌더 Mixin 대상이 `Hud`/`GuiRenderer`로 이동 (`GuiRenderer.render()` 시그니처 변화) → **GUI 내부 Mixin은 버전마다 깨짐 → 우리의 "공식 경로+Mixin 최소화" 원칙 확인됨.**
- `Minecraft#setScreen` → `Minecraft.getInstance().gui.setScreen()`.
- `MultiBufferSource` 제거 (대체: `PreparedRenderType` 계열).
- 호환 주의: ImmediatelyFast 26.2 폰트 렌더 버그(이슈 #542), 26.3 파이프라인 룩업이 vertex format 무시하는 충돌 사례 → 커스텀 `RenderPipeline` 등록 시 key 충돌 주의.
- 필요 하드웨어: Vulkan 1.2 + dynamic rendering + push descriptors, 미지원 시 OpenGL 폴백 (→ **백엔드는 어느 쪽 렌더러에서도 동일하게 동작해야 함**: 우리는 Blaze3D 추상화 위에서만 그리므로 OK).

## 4. 타깃 결정 (권고)
1. **1차 타깃: 26.3** — 유일한 안정판이며 Fabric·NeoForge 둘 다 지원. 사용자는 Vulkan을 켜고 테스트 가능(옵션 설정).
2. **26.4 대응**: Fabric은 이미 가능, NeoForge는 출시 대기. 백엔드를 `backend-mc26` 로 두고 26.3↔26.4 차이는 Stonecutter 분기로 처리. 26.4 정식 + NeoForge 26.4 출시 시 2번째 타깃으로 확장.
3. GL 전용 구버전(1.21.x 등) 백엔드는 **우선순위 낮춤**: 26.1에서 비난독화·툴체인이 바뀌어 별도 빌드 라인이 필요하고, OpenGL은 제거 예정이므로 M4.6(두 번째 백엔드 검증)은 "26.3 ↔ 26.4" 로 대체 권고.
4. Sodium/Iris/ImmediatelyFast 등의 26.3 지원 상태는 M0 후반에 모드 페이지에서 확인 후 호환 매트릭스 확정.

## 5. 미확정 / 후속 확인 (코드 작성 시 직접 검증)
- [ ] Loom 정확한 버전 및 Gradle 9.x 호환
- [ ] 26.3에서 2D GUI 진입점: `GuiGraphics`(fill/blit/text/clip/pose) 정확한 시그니처, `Hud` 레이어 등록용 Fabric/NeoForge 공식 이벤트 이름
- [ ] `DynamicTexture`/`GpuTexture` 업로드 API (이미지·영상 프레임용)
- [ ] 아이템/엔티티 GUI 렌더 함수 변화
- [ ] 블러/포스트 효과 접근 방식 (커스텀 RenderPipeline 필요 여부)
- [ ] 서버 동기화용 payload API (Fabric Networking / NeoForge payload) 26.x 시그니처
- [ ] 26.4에서 `minecraft:mod_list` payload 등 신규 네트워크 변화가 우리 핸드셰이크에 영향 주는지

## 출처
- Fabric for 26.2: https://fabricmc.net/2026/06/15/262.html
- NeoForged 26.2 primer: https://docs.neoforged.net/primer/docs/26.2/
- Fabric porting 26.1: https://docs.fabricmc.net/develop/porting/
- Minecraft Wiki 26.4 Snapshot 1: https://minecraft.wiki/w/Java_Edition_26.4_Snapshot_1
- Fabric meta API / maven (fabric-api 0.165.x), NeoForge maven (26.3.0.101-beta)
