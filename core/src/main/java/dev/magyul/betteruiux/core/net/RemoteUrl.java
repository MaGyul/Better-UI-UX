package dev.magyul.betteruiux.core.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 원격 미디어 주소 규칙: {@code https://호스트/경로#sha256=<64자리 16진수>}.
 * https 만, 기본 포트(443)만, 사용자 정보/IP 주소 직접 입력 불가. {@code #sha256=} 은 선택이지만 있으면 받은 내용이 정확히 그 해시여야 한다.
 */
public final class RemoteUrl {
    private RemoteUrl() {}

    public static final int MAX_LENGTH = 512;

    /** 요청할 주소(핀 조각 제외)와 핀(없으면 null). */
    public record Parsed(URI uri, byte[] pin) {}

    public static boolean looksRemote(String src) { return src != null && src.regionMatches(true, 0, "https://", 0, 8); }

    public static Parsed parse(String src) throws RemoteException {
        if (src == null || src.length() > MAX_LENGTH) throw new RemoteException("주소가 비었거나 너무 깁니다");
        URI u;
        try {
            u = new URI(src.trim());
        } catch (URISyntaxException e) {
            throw new RemoteException("주소 형식이 올바르지 않습니다");
        }
        byte[] pin = null;
        String frag = u.getRawFragment();
        if (frag != null) {
            if (!frag.toLowerCase(Locale.ROOT).startsWith("sha256=") || frag.length() != 7 + 64) throw new RemoteException("조각은 #sha256=<64자리 16진수> 형식이어야 합니다");
            try {
                pin = HexFormat.of().parseHex(frag.substring(7));
            } catch (IllegalArgumentException e) {
                throw new RemoteException("sha256 값이 16진수가 아닙니다");
            }
        }
        URI request = check(strip(u));
        return new Parsed(request, pin);
    }

    private static URI strip(URI u) throws RemoteException {
        try {
            return new URI(u.getScheme(), u.getRawAuthority(), u.getPath(), u.getQuery(), null);
        } catch (URISyntaxException e) {
            throw new RemoteException("주소 형식이 올바르지 않습니다");
        }
    }

    /** 요청(리다이렉트 포함)마다 부르는 검사: https, 기본 포트, 호스트 이름(IP 직접 입력 금지), 사용자 정보 없음. */
    public static URI check(URI u) throws RemoteException {
        if (u.getScheme() == null || !u.getScheme().equalsIgnoreCase("https")) throw new RemoteException("https 주소만 쓸 수 있습니다");
        if (u.getRawUserInfo() != null) throw new RemoteException("주소에 사용자 정보를 넣을 수 없습니다");
        String host = u.getHost();
        if (host == null || host.isEmpty()) throw new RemoteException("호스트가 없습니다");
        if (u.getPort() != -1 && u.getPort() != 443) throw new RemoteException("포트는 443 만 쓸 수 있습니다");
        if (host.indexOf(':') >= 0 || host.matches("[0-9.]+") || host.startsWith("[")) throw new RemoteException("IP 주소로는 접속할 수 없습니다(도메인 이름을 쓰세요)");
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan")) throw new RemoteException("내부 주소는 쓸 수 없습니다");
        return u;
    }

    /** 이름 풀이 결과가 모두 공개 인터넷 주소인가(사설/루프백/링크로컬 등은 거부 — 내 네트워크 안쪽으로 요청이 가지 않게). */
    public static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
            if (b0 == 0) return false;                                  // 0.0.0.0/8
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;       // 100.64.0.0/10 (CGNAT)
            if (b0 == 192 && b1 == 0 && (b[2] & 0xFF) == 0) return false; // 192.0.0.0/24
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false;      // 198.18.0.0/15 (벤치마크)
            if (b0 >= 240) return false;                                // 예약/브로드캐스트
        } else if (b.length == 16) {
            if ((b[0] & 0xFE) == 0xFC) return false;                    // fc00::/7 (유니크 로컬)
        }
        return true;
    }
}
