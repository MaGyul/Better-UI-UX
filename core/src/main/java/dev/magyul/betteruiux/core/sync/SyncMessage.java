package dev.magyul.betteruiux.core.sync;

import java.util.List;
import java.util.Map;

/**
 * 서버 동기화 메시지. 방향: C2S(클라이언트 → 서버), S2C(서버 → 클라이언트).
 * 값({@code Object})은 {@code String}, {@code Long}, {@code Double}, {@code Boolean} 만 쓴다.
 */
public sealed interface SyncMessage {
    /** C2S: 클라이언트 인사. {@code bundleHash} 는 캐시에 가진 번들 해시(없으면 빈 문자열). */
    record ClientHello(int protocol, String bundleHash) implements SyncMessage {}

    /** S2C: 서버 인사. {@code policy}: 0 = server-first(서버 번들이 같은 id 의 로컬 파일을 덮음), 1 = local-first. */
    record ServerHello(int protocol, String bundleHash, int policy) implements SyncMessage {}

    /** 번들 파일 한 개의 정보. {@code sha256} 은 32바이트. */
    record FileEntry(String path, byte[] sha256, long size) {}

    /** S2C: 번들 목록. */
    record BundleManifest(String hash, List<FileEntry> files) implements SyncMessage {}

    /** C2S: 필요한 파일(캐시에 없거나 해시가 다른 것). */
    record BundleNeed(List<String> paths) implements SyncMessage {}

    /** S2C: 파일 조각. */
    record BundleChunk(String path, int index, int total, byte[] data) implements SyncMessage {}

    /** S2C: 화면 열기. */
    record OpenUi(String id, Map<String, Object> params) implements SyncMessage {}

    /** S2C: 화면 닫기. */
    record CloseUi(String id) implements SyncMessage {}

    /** S2C: 값 하나 설정. */
    record StateSet(String key, Object value) implements SyncMessage {}

    /** S2C: 값 하나 지우기. */
    record StateClear(String key) implements SyncMessage {}

    /** S2C: 전체 값(접속 직후나 번들 갱신 후). */
    record StateSnapshot(Map<String, Object> values) implements SyncMessage {}

    /** C2S: 버튼 동작. */
    record Action(String uiId, String actionId, Map<String, Object> args) implements SyncMessage {}

    /** C2S: 클라이언트가 서버가 연 화면을 닫았다(서버는 그 화면의 동작을 더 받지 않는다). */
    record UiClosed(String id) implements SyncMessage {}

    /** C2S: 에디터에서 서버로 발행 시작. 파일 목록(최대 {@link SyncLimits#MAX_PUBLISH_FILES}개, 합계 {@link SyncLimits#MAX_PUBLISH_BYTES}). */
    record PublishBegin(BundleManifest manifest) implements SyncMessage {}

    /** S2C: 발행 진행 결과. {@code stage} 0 = 시작 승인/거부(승인이면 조각을 보낸다), 1 = 저장 완료/실패. */
    record PublishResult(int stage, boolean ok, String message) implements SyncMessage {}
}
