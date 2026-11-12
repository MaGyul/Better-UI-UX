package dev.magyul.betteruiux.core.net;

/** 원격 미디어를 가져올 수 없을 때(허용되지 않는 주소, 크기 초과, 해시 불일치, 네트워크 오류 등). 메시지는 사용자에게 보여도 된다. */
public final class RemoteException extends Exception {
    public RemoteException(String message) { super(message); }

    public RemoteException(String message, Throwable cause) { super(message, cause); }
}
