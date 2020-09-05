package com.github.alanger.webdav;

import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See org.apache.jackrabbit.webdav.simple.LocatorFactoryImpl
 */
public class VfsDavLocatorFactory implements DavLocatorFactory {

    /** the default logger */
    private static final Logger log = LoggerFactory.getLogger(VfsDavLocatorFactory.class);

    private final String repositoryPrefix;

    public VfsDavLocatorFactory(String repositoryPrefix) {
        this.repositoryPrefix = repositoryPrefix;
    }

    public DavResourceLocator createResourceLocator(String prefix, String href) {
        if (log.isTraceEnabled())
            log.trace("# createResourceLocator prefix: {}, href: {}", prefix, href);

        // build prefix string.
        StringBuilder b = new StringBuilder("");
        if (prefix != null && prefix.length() > 0) {
            b.append(prefix);
        }

        // special treatment for root item, that has no name but '/' path.
        if (href == null || "".equals(href)) {
            href = "/";
        }

        return new Locator(b.toString(), Text.unescape(href), this);
    }

    public DavResourceLocator createResourceLocator(String prefix, String workspacePath, String resourcePath) {
        return createResourceLocator(prefix, workspacePath, resourcePath, true);
    }

    public DavResourceLocator createResourceLocator(String prefix, String workspacePath, String path,
            boolean isResourcePath) {
        if (log.isTraceEnabled())
            log.trace("# createResourceLocator prefix: {}, workspacePath: {}, path: {}, isResourcePath: {}", prefix,
                    workspacePath, path, isResourcePath);
        return new Locator(prefix, path, this);
    }

    // --------------------------------------------------------------------------
    protected class Locator implements DavResourceLocator {

        private final String prefix;
        private final String resourcePath;
        private final String repositoryPath;
        private final DavLocatorFactory factory;
        private final String href;

        private Locator(String prefix, String resourcePath, DavLocatorFactory factory) {
            this.prefix = prefix;
            this.factory = factory;
            // remove trailing '/' that is not part of the resourcePath except for the root item.
            if (resourcePath.endsWith("/") && !"/".equals(resourcePath)) {
                resourcePath = resourcePath.substring(0, resourcePath.length() - 1);
            }

            this.resourcePath = resourcePath;
            this.repositoryPath = resourcePath.replaceAll("^/", "");
            href = prefix + Text.escapePath(resourcePath);

            if (log.isTraceEnabled())
                log.trace("# Locator: {}, {}, {}", prefix, resourcePath, href);
        }

        public String getPrefix() {
            return prefix;
        }

        public String getResourcePath() {
            return resourcePath;
        }

        public String getWorkspacePath() {
            return "";
        }

        public String getWorkspaceName() {
            return repositoryPrefix;
        }

        public boolean isSameWorkspace(DavResourceLocator locator) {
            return isSameWorkspace(locator.getWorkspaceName());
        }

        public boolean isSameWorkspace(String workspaceName) {
            return getWorkspaceName().equals(workspaceName);
        }

        public String getHref(boolean isCollection) {
            // avoid doubled trailing '/' for the root item
            String suffix = (isCollection && !isRootLocation()) ? "/" : "";
            return href + suffix;
        }

        public boolean isRootLocation() {
            return "/".equals(resourcePath);
        }

        public DavLocatorFactory getFactory() {
            return factory;
        }

        /**
         * Returns the same as {@link #getResourcePath()}. No encoding is performed at all.
         * 
         * @see DavResourceLocator#getRepositoryPath()
         */
        public String getRepositoryPath() {
            return this.repositoryPath;
        }

        /**
         * Computes the hash code from the href, which is built using the final fields prefix and resourcePath.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return href.hashCode();
        }

        /**
         * Equality of path is achieved if the specified object is a <code>DavResourceLocator</code> object with the
         * same hash code.
         *
         * @param obj
         *            the object to compare to
         * @return <code>true</code> if the 2 objects are equal; <code>false</code> otherwise
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DavResourceLocator) {
                DavResourceLocator other = (DavResourceLocator) obj;
                return hashCode() == other.hashCode();
            }
            return false;
        }
    }

}
