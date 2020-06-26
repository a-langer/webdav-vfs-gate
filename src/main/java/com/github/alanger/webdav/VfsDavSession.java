package com.github.alanger.webdav;

import java.util.HashSet;

import org.apache.jackrabbit.webdav.DavSession;

/**
 * See org.apache.jackrabbit.webdav.simple.DavSessionImpl
 */
public class VfsDavSession implements DavSession {

    /** the lock tokens of this session */
    private final HashSet<String> lockTokens = new HashSet<String>();

    @Override
    public void addReference(Object reference) {
        throw new UnsupportedOperationException("No yet implemented.");
    }

    @Override
    public void removeReference(Object reference) {
        throw new UnsupportedOperationException("No yet implemented.");
    }

    @Override
    public void addLockToken(String token) {
        lockTokens.add(token);
    }

    @Override
    public String[] getLockTokens() {
        return lockTokens.toArray(new String[lockTokens.size()]);
    }

    @Override
    public void removeLockToken(String token) {
        lockTokens.remove(token);
    }

}
