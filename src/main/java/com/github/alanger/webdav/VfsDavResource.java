package com.github.alanger.webdav;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.jackrabbit.webdav.DavCompliance;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockDiscovery;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.lock.LockManager;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.SupportedLock;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.property.PropEntry;
import org.apache.jackrabbit.webdav.property.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See org.apache.jackrabbit.webdav.simple.DavResourceImpl
 */
public class VfsDavResource implements DavResource {

    private static final Logger log = LoggerFactory.getLogger(VfsDavResource.class);

    public static final String METHODS = DavResource.METHODS;

    public static final String COMPLIANCE_CLASSES = DavCompliance
            .concatComplianceClasses(new String[] { DavCompliance._1_, DavCompliance._2_, DavCompliance._3_ });

    public static final String UTF_8 = "UTF-8";

    private DavResourceFactory factory;
    private LockManager lockManager;
    private DavSession session;
    private DavResourceLocator locator;
    private FileObject fileObject = null;

    protected DavPropertySet properties;
    protected boolean propsInitialized = false;
    private boolean isCollection = false;

    private long modificationTime = DavConstants.UNDEFINED_TIME;
    private long contentLength = IOUtil.UNDEFINED_LENGTH;

    public VfsDavResource(DavResourceLocator locator, DavResourceFactory factory, DavSession session,
            boolean isCollection) throws DavException {
        this(locator, factory, session, null);
        this.isCollection = isCollection;
    }

    public VfsDavResource(DavResourceLocator locator, DavResourceFactory factory, DavSession session,
            FileObject fileObject) throws DavException {
        if (locator == null || session == null) {
            throw new IllegalArgumentException("Locator or session is null");
        }

        this.session = session;
        this.factory = factory;
        this.locator = locator;

        if (locator.getResourcePath() != null) {
            if (fileObject != null) {
                this.fileObject = fileObject;
                this.properties = new DavPropertySet();
                // define what is a collection in webdav
                try {
                    this.isCollection = fileObject.isFolder();
                } catch (FileSystemException e) {
                    this.fileObject = null;
                    final String msg = "Failed to check if it is a folder at '" + getResourcePath() + "'";
                    log.debug(msg, e);
                    throw new DavException(DavServletResponse.SC_NOT_FOUND);
                }
            }
        } else {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    public String getComplianceClass() {
        return COMPLIANCE_CLASSES;
    }

    @Override
    public String getSupportedMethods() {
        return METHODS;
    }

    @Override
    public boolean exists() {
        return fileObject != null;
    }

    @Override
    public boolean isCollection() {
        return isCollection;
    }

    @Override
    public String getDisplayName() {
        String resPath = getResourcePath();
        return (resPath != null) ? Text.getName(resPath) : resPath;
    }

    @Override
    public DavResourceLocator getLocator() {
        return locator;
    }

    @Override
    public String getResourcePath() {
        return locator.getResourcePath();
    }

    @Override
    public String getHref() {
        return locator.getHref(isCollection());
    }

    @Override
    public long getModificationTime() {
        initProperties();
        return modificationTime;
    }

    public long getContentLength() {
        initProperties();
        return contentLength;
    }

    @Override
    public void spool(OutputContext outputContext) throws IOException {
        if (exists() && !isCollection() && outputContext != null) {
            outputContext.setContentLength(getContentLength());
            try (InputStream is = fileObject.getContent().getInputStream();
                    OutputStream os = outputContext.getOutputStream();) {
                if (os != null) { // HEAD method
                    byte[] buffer = new byte[8 * 1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
    }

    @Override
    public DavProperty<?> getProperty(DavPropertyName name) {
        initProperties();
        return properties.get(name);
    }

    @Override
    public DavPropertySet getProperties() {
        initProperties();
        return properties;
    }

    @Override
    public DavPropertyName[] getPropertyNames() {
        return getProperties().getPropertyNames();
    }

    /**
     * Fill the set of properties
     */
    protected void initProperties() {
        if (!exists() || propsInitialized) {
            return;
        }

        if (log.isTraceEnabled())
            log.trace("# initProperties: {}, exist: {}, isCollection: {}", getResourcePath(), exists(), isCollection());

        PropertyExportCtx context = new PropertyExportCtx();

        String mimeType = "application/octet-stream";

        if (exists()) {
            try (FileContent content = fileObject.getContent()) {
                if (!isCollection()) {
                    contentLength = content.getSize();
                    // Set "content.types.user.table" properties, $JRE_HOME/lib/content-types.properties
                    mimeType = content.getContentInfo().getContentType();
                } else {
                    mimeType = "inode/directory";
                }
                modificationTime = content.getLastModifiedTime();
            } catch (FileSystemException e) {
                final String msg = "Failed while initialize properties at '" + getResourcePath() + "'";
                log.debug(msg, e);
            }
        }

        context.setModificationTime(modificationTime);
        context.setCreationTime(modificationTime);
        context.setContentLength(contentLength);
        context.setContentType(mimeType, /* UTF_8 */ null);
        if (contentLength > IOUtil.UNDEFINED_LENGTH && modificationTime > IOUtil.UNDEFINED_TIME) {
            String etag = "\"" + contentLength + "-" + modificationTime + "\"";
            context.setETag(etag);
        }

        // set (or reset) fundamental properties
        if (getDisplayName() != null) {
            properties.add(new DefaultDavProperty<>(DavPropertyName.DISPLAYNAME, getDisplayName()));
        }
        if (isCollection()) {
            properties.add(new ResourceType(ResourceType.COLLECTION));
            // Windows XP support
            properties.add(new DefaultDavProperty<>(DavPropertyName.ISCOLLECTION, "1"));
        } else {
            properties.add(new ResourceType(ResourceType.DEFAULT_RESOURCE));
            // Windows XP support
            properties.add(new DefaultDavProperty<>(DavPropertyName.ISCOLLECTION, "0"));
        }

        /*
         * set current lock information. If no lock is set to this resource, an empty lock discovery will be returned in
         * the response.
         */
        properties.add(new LockDiscovery(getLock(Type.WRITE, Scope.EXCLUSIVE)));

        /* lock support information: all locks are lockable. */
        SupportedLock supportedLock = new SupportedLock();
        supportedLock.addEntry(Type.WRITE, Scope.EXCLUSIVE);
        properties.add(supportedLock);

        propsInitialized = true;
    }

    /**
     * See org.apache.jackrabbit.webdav.simple.DavResourceImpl.PropertyExportCtx
     */
    protected class PropertyExportCtx {
        private PropertyExportCtx() {
            // set defaults:
            setCreationTime(IOUtil.UNDEFINED_TIME);
            setModificationTime(IOUtil.UNDEFINED_TIME);
        }

        public void setContentLanguage(String contentLanguage) {
            if (contentLanguage != null) {
                properties.add(new DefaultDavProperty<>(DavPropertyName.GETCONTENTLANGUAGE, contentLanguage));
            }
        }

        public void setContentLength(long contentLength) {
            if (contentLength > IOUtil.UNDEFINED_LENGTH) {
                properties.add(new DefaultDavProperty<>(DavPropertyName.GETCONTENTLENGTH, contentLength + ""));
            }
        }

        public void setContentType(String mimeType, String encoding) {
            String contentType = IOUtil.buildContentType(mimeType, encoding);
            if (contentType != null) {
                properties.add(new DefaultDavProperty<>(DavPropertyName.GETCONTENTTYPE, contentType));
            }
        }

        public void setCreationTime(long creationTime) {
            String created = IOUtil.getCreated(creationTime);
            properties.add(new DefaultDavProperty<>(DavPropertyName.CREATIONDATE, created));
        }

        public void setModificationTime(long modTime) {
            if (modTime <= IOUtil.UNDEFINED_TIME) {
                modificationTime = new Date().getTime();
            } else {
                modificationTime = modTime;
            }
            String lastModified = IOUtil.getLastModified(modificationTime);
            properties.add(new DefaultDavProperty<>(DavPropertyName.GETLASTMODIFIED, lastModified));
        }

        public void setETag(String etag) {
            if (etag != null) {
                properties.add(new DefaultDavProperty<>(DavPropertyName.GETETAG, etag));
            }
        }

        public void setProperty(Object propertyName, Object propertyValue) {
            if (propertyValue == null) {
                log.warn("Ignore 'setProperty' for {} with null value.", propertyName);
                return;
            }

            if (propertyValue instanceof DavProperty) {
                properties.add((DavProperty<?>) propertyValue);
            } else {
                DavPropertyName pName;
                if (propertyName instanceof DavPropertyName) {
                    pName = (DavPropertyName) propertyName;
                } else {
                    // create property name with default DAV: namespace
                    pName = DavPropertyName.create(propertyName.toString());
                }
                properties.add(new DefaultDavProperty<>(pName, propertyValue));
            }
        }
    }

    @Override
    public void setProperty(DavProperty<?> property) throws DavException {
        throw new DavException(DavServletResponse.SC_NOT_IMPLEMENTED);
    }

    @Override
    public void removeProperty(DavPropertyName propertyName) throws DavException {
        throw new DavException(DavServletResponse.SC_NOT_IMPLEMENTED);
    }

    @Override
    public MultiStatusResponse alterProperties(List<? extends PropEntry> changeList) throws DavException {
        if (log.isTraceEnabled())
            log.trace("# alterProperties: {}, exist: {}, isCollection: {}", getResourcePath(), exists(),
                    isCollection());

        if (isLocked(this)) {
            throw new DavException(DavServletResponse.SC_LOCKED);
        }
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        MultiStatusResponse msr = new MultiStatusResponse(getHref(), null);

        Map<? extends PropEntry, ?> failures = new HashMap<>(); // <PropEntry, RepositoryException>

        /*
         * loop over list of properties/names that were successfully altered and them to the multistatus response
         * respecting the result of the complete action. in case of failure set the status to 'failed-dependency' in
         * order to indicate, that altering those names/properties would have succeeded, if no other error occured.
         */
        for (PropEntry propEntry : changeList) {
            int statusCode;
            if (failures.containsKey(propEntry)) {
                Object error = failures.get(propEntry);
                statusCode = (error instanceof DavException) ? ((DavException) error).getErrorCode()
                        : DavServletResponse.SC_INTERNAL_SERVER_ERROR;
            } else {
                statusCode = (failures.isEmpty()) ? DavServletResponse.SC_OK : DavServletResponse.SC_FAILED_DEPENDENCY;
            }
            if (propEntry instanceof DavProperty) {
                msr.add(((DavProperty<?>) propEntry).getName(), statusCode);
            } else {
                msr.add((DavPropertyName) propEntry, statusCode);
            }
        }
        return msr;
    }

    @Override
    public DavResource getCollection() {
        if (log.isTraceEnabled())
            log.trace("# getCollection: {}, exist: {}, isCollection: {}", getResourcePath(), exists(), isCollection());

        DavResource parent = null;

        if (getResourcePath() != null && !getResourcePath().equals("/")) {
            String parentPath = Text.getRelativeParent(getResourcePath(), 1);
            if (parentPath.equals("")) {
                parentPath = "/";
            }

            DavResourceLocator parentloc = locator.getFactory().createResourceLocator(locator.getPrefix(),
                    locator.getWorkspacePath(), parentPath);
            try {
                parent = factory.createResource(parentloc, session);
            } catch (DavException e) {
                // should not occur
                log.debug("getCollection error", e);
            }
        }
        return parent;
    }

    @Override
    public DavResourceIterator getMembers() {
        if (log.isTraceEnabled())
            log.trace("# getMembers: {}, exist: {}, isCollection: {}", getResourcePath(), exists(), isCollection());

        ArrayList<DavResource> list = new ArrayList<>();
        if (exists() && isCollection()) {
            try {
                String parentPath = getResourcePath();
                if (!parentPath.endsWith("/")) {
                    parentPath = parentPath + "/";
                }

                FileObject[] children = fileObject.getChildren();
                for (FileObject n : children) {
                    String path = parentPath + n.getName().getBaseName();

                    DavResourceLocator resourceLocator = locator.getFactory().createResourceLocator(locator.getPrefix(),
                            locator.getWorkspacePath(), path, false);
                    DavResource childRes = factory.createResource(resourceLocator, session);

                    if (childRes != null && childRes.exists())
                        list.add(childRes);
                }
            } catch (FileSystemException e) {
                // should not occur
                log.debug("getMembers FS error", e);
            } catch (DavException e) {
                // should not occur
                log.debug("getMembers Dav error", e);
            }
        }
        return new DavResourceIteratorImpl(list);
    }

    @Override
    public void addMember(DavResource member, InputContext inputContext) throws DavException {
        if (log.isTraceEnabled())
            log.trace("# addMember: {}", member.getResourcePath());

        if (!exists()) {
            throw new DavException(DavServletResponse.SC_CONFLICT);
        }
        if (isLocked(this) || isLocked(member)) {
            throw new DavException(DavServletResponse.SC_LOCKED);
        }
        try {
            String memberName = Text.getName(member.getLocator().getRepositoryPath());
            FileObject child = fileObject.resolveFile(memberName);
            if (member.isCollection()) {
                child.createFolder();
            } else {
                child.createFile();
                try (InputStream is = inputContext.getInputStream();
                        OutputStream os = child.getContent().getOutputStream();) {
                    byte[] buffer = new byte[8 * 1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error while importing resource: {}", e.toString());
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public void removeMember(DavResource member) throws DavException {
        if (log.isTraceEnabled())
            log.trace("# removeMember: {}", member.getResourcePath());

        if (!exists() || !member.exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        if (isLocked(this) || isLocked(member)) {
            throw new DavException(DavServletResponse.SC_LOCKED);
        }

        try {
            String memberName = Text.getName(member.getLocator().getRepositoryPath());
            FileObject child = fileObject.getChild(memberName);
            child.deleteAll();
        } catch (IOException e) {
            log.error("Error while remove member: {}", e.toString());
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }

        // make sure, non-jcr locks are removed, once the removal is completed
        try {
            ActiveLock lock = getLock(Type.WRITE, Scope.EXCLUSIVE);
            if (lock != null) {
                lockManager.releaseLock(lock.getToken(), member);
            }
        } catch (DavException e) {
            // since check for 'locked' exception has been performed before
            // ignore any error here
        }
    }

    @Override
    public void move(DavResource destination) throws DavException {
        if (log.isTraceEnabled())
            log.trace("# move: {} - {} to {} - {}", getResourcePath(), exists(), destination.getResourcePath(),
                    destination.exists());

        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        if (isLocked(this)) {
            throw new DavException(DavServletResponse.SC_LOCKED);
        }
        // make sure, that src and destination belong to the same workspace
        checkSameWorkspace(destination.getLocator());

        try {
            FileObject destRootObject = ((VfsDavResourceFactory) destination.getFactory()).getRootObject();
            FileObject destFile = destRootObject.resolveFile(destination.getLocator().getRepositoryPath());
            fileObject.moveTo(destFile);
        } catch (IOException e) {
            log.error("Error while move resource: {}", e.toString());
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public void copy(DavResource destination, boolean shallow) throws DavException {
        if (log.isTraceEnabled())
            log.trace("# copy: {} - {} to {} - {}", getResourcePath(), exists(), destination.getResourcePath(),
                    destination.exists());

        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        if (isLocked(destination)) {
            throw new DavException(DavServletResponse.SC_LOCKED);
        }

        // make sure, that src and destination belong to the same workspace
        checkSameWorkspace(destination.getLocator());

        try {
            FileObject destRootObject = ((VfsDavResourceFactory) destination.getFactory()).getRootObject();
            FileObject destFile = destRootObject.resolveFile(destination.getLocator().getRepositoryPath());
            destFile.copyFrom(fileObject, new AllFileSelector());
        } catch (IOException e) {
            log.error("Error while copy resource: {}", e.toString());
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void checkSameWorkspace(DavResourceLocator otherLoc) throws DavException {
        String wspname = getLocator().getWorkspaceName();
        if (!wspname.equals(otherLoc.getWorkspaceName())) {
            throw new DavException(DavServletResponse.SC_FORBIDDEN,
                    "Workspace mismatch: expected '" + wspname + "'; found: '" + otherLoc.getWorkspaceName() + "'");
        }
    }

    private boolean isLocked(DavResource res) {
        ActiveLock lock = res.getLock(Type.WRITE, Scope.EXCLUSIVE);
        if (lock == null) {
            return false;
        } else {
            for (String sLockToken : session.getLockTokens()) {
                if (sLockToken.equals(lock.getToken())) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public boolean isLockable(Type type, Scope scope) {
        return Type.WRITE.equals(type) && Scope.EXCLUSIVE.equals(scope);
    }

    @Override
    public boolean hasLock(Type type, Scope scope) {
        return getLock(type, scope) != null;
    }

    @Override
    public ActiveLock getLock(Type type, Scope scope) {
        ActiveLock lock = null;
        if (exists() && Type.WRITE.equals(type) && Scope.EXCLUSIVE.equals(scope)) {
            // simple webdav lock is present.
            lock = lockManager.getLock(type, scope, this);
        }
        return lock;
    }

    @Override
    public ActiveLock[] getLocks() {
        ActiveLock writeLock = getLock(Type.WRITE, Scope.EXCLUSIVE);
        return (writeLock != null) ? new ActiveLock[] { writeLock } : new ActiveLock[0];
    }

    @Override
    public ActiveLock lock(LockInfo lockInfo) throws DavException {
        ActiveLock lock = null;
        if (isLockable(lockInfo.getType(), lockInfo.getScope())) {
            // create a new webdav lock
            lock = lockManager.createLock(lockInfo, this);
        } else {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED, "Unsupported lock type or scope.");
        }
        return lock;
    }

    @Override
    public ActiveLock refreshLock(LockInfo lockInfo, String lockToken) throws DavException {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        ActiveLock lock = getLock(lockInfo.getType(), lockInfo.getScope());
        if (lock == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "No lock with the given type/scope present on resource " + getResourcePath());
        }

        lock = lockManager.refreshLock(lockInfo, lockToken, this);
        return lock;
    }

    @Override
    public void unlock(String lockToken) throws DavException {
        ActiveLock lock = getLock(Type.WRITE, Scope.EXCLUSIVE);
        if (lock == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
        } else if (lock.isLockedByToken(lockToken)) {
            lockManager.releaseLock(lockToken, this);
        } else {
            throw new DavException(DavServletResponse.SC_LOCKED);
        }
    }

    @Override
    public void addLockManager(LockManager lockMgr) {
        this.lockManager = lockMgr;
    }

    @Override
    public DavResourceFactory getFactory() {
        return factory;
    }

    @Override
    public DavSession getSession() {
        return session;
    }

}
