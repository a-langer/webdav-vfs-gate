package com.github.alanger.webdav;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.vfs2.CacheStrategy;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FilesCache;
import org.apache.commons.vfs2.UserAuthenticator;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.cache.SoftRefFilesCache;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.webdav4.Webdav4FileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.zip.ZipFileSystemConfigBuilder;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavSessionProvider;
import org.apache.jackrabbit.webdav.WebdavRequest;
import org.apache.jackrabbit.webdav.WebdavResponse;
import org.apache.jackrabbit.webdav.lock.LockManager;
import org.apache.jackrabbit.webdav.lock.SimpleLockManager;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.server.AbstractWebdavServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VfsWebDavServlet extends AbstractWebdavServlet {

    private static final long serialVersionUID = 1L;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String VERSION = VfsWebDavServlet.class.getPackage().getImplementationVersion() != null
            ? VfsWebDavServlet.class.getPackage().getImplementationVersion()
            : "unknown";
    public static final String INIT_PARAM_ROOTPATH = "rootpath";
    public static final String INIT_PARAM_DOMAIN = "domain";
    public static final String INIT_PARAM_LOGIN = "login";
    public static final String INIT_PARAM_PASSWORD = "password";
    public static final String INIT_PARAM_LISTINGS = "listings-directory";
    public static final String INIT_PARAM_INCTXPATH = "include-context-path";
    public static final String INIT_PARAM_FILESCACHE = "files-cache";
    public static final String INIT_PARAM_CACHESTRATEGY = "cache-strategy";
    public static final String INIT_PARAM_BUILDER = "builder";
    public static final String INIT_PARAM_LOGGER = "logger-name";
    public static final String INIT_PARAM_AUDMETHODS = "audit-methods";

    private boolean listingsDirectory = true;
    private boolean includeContextPath = true;
    private List<String> auditMethods = null;

    private DavSessionProvider davSessionProvider;
    private DavLocatorFactory locatorFactory;
    private LockManager lockManager;
    private DavResourceFactory resourceFactory;
    private UserAuthenticator userAuthenticator;
    private FileSystemOptions fileSystemOptions;
    private FilesCache filesCache;
    private CacheStrategy cacheStrategy;
    private FileSystemManager fileSystemManager;
    private FileObject fileObject;

    @Override
    protected boolean isPreconditionValid(WebdavRequest request, DavResource resource) {
        return resource != null && (!resource.exists() || request.matchesIfHeader(resource));
    }

    @Override
    public DavSessionProvider getDavSessionProvider() {
        if (davSessionProvider == null) {
            davSessionProvider = new VfsDavSessionProvider();
        }
        return davSessionProvider;
    }

    @Override
    public void setDavSessionProvider(DavSessionProvider davSessionProvider) {
        this.davSessionProvider = davSessionProvider;
    }

    @Override
    public DavLocatorFactory getLocatorFactory() {
        if (locatorFactory == null) {
            locatorFactory = new VfsDavLocatorFactory(getServletName());
        }
        return locatorFactory;
    }

    @Override
    public void setLocatorFactory(DavLocatorFactory locatorFactory) {
        this.locatorFactory = locatorFactory;
    }

    public LockManager getLockManager() {
        if (lockManager == null) {
            lockManager = new SimpleLockManager();
        }
        return lockManager;
    }

    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    @Override
    public DavResourceFactory getResourceFactory() {
        if (resourceFactory == null) {
            resourceFactory = new VfsDavResourceFactory(getLockManager(), fileObject);
        }
        return resourceFactory;
    }

    @Override
    public void setResourceFactory(DavResourceFactory resourceFactory) {
        this.resourceFactory = resourceFactory;
    }

    public UserAuthenticator getUserAuthenticator() {
        return userAuthenticator;
    }

    public void setUserAuthenticator(UserAuthenticator userAuthenticator) {
        this.userAuthenticator = userAuthenticator;
    }

    public FileSystemOptions getFileSystemOptions() throws FileSystemException {
        if (fileSystemOptions == null) {
            fileSystemOptions = new FileSystemOptions();

            ZipFileSystemConfigBuilder zipBuilder = ZipFileSystemConfigBuilder.getInstance();
            zipBuilder.setCharset(fileSystemOptions, StandardCharsets.UTF_8);

            FtpsFileSystemConfigBuilder ftpsBuilder = FtpsFileSystemConfigBuilder.getInstance();
            ftpsBuilder.setConnectTimeout(fileSystemOptions, 1000 * 5);
            ftpsBuilder.setUserDirIsRoot(fileSystemOptions, false);
            ftpsBuilder.setAutodetectUtf8(fileSystemOptions, true);
            ftpsBuilder.setPassiveMode(fileSystemOptions, true);
            ftpsBuilder.setControlEncoding(fileSystemOptions, StandardCharsets.UTF_8.name());

            SftpFileSystemConfigBuilder sftpBuilder = SftpFileSystemConfigBuilder.getInstance();
            sftpBuilder.setConnectTimeoutMillis(fileSystemOptions, 1000 * 5);
            sftpBuilder.setUserDirIsRoot(fileSystemOptions, false);
            sftpBuilder.setStrictHostKeyChecking(fileSystemOptions, "no");

            Webdav4FileSystemConfigBuilder webdavBuilder = Webdav4FileSystemConfigBuilder.getInstance();
            webdavBuilder.setConnectionTimeout(fileSystemOptions, 1000 * 5 * 10);
            webdavBuilder.setSoTimeout(fileSystemOptions, 1000 * 5 * 10);
            webdavBuilder.setMaxConnectionsPerHost(fileSystemOptions, 1000);
            webdavBuilder.setMaxTotalConnections(fileSystemOptions, 1000);
            webdavBuilder.setHostnameVerificationEnabled(fileSystemOptions, false);
            webdavBuilder.setPreemptiveAuth(fileSystemOptions, true);
            webdavBuilder.setUrlCharset(fileSystemOptions, StandardCharsets.UTF_8.name());
            webdavBuilder.setFollowRedirect(fileSystemOptions, true);
            webdavBuilder.setKeepAlive(fileSystemOptions, true);
        }
        if (userAuthenticator != null) {
            DefaultFileSystemConfigBuilder defaultBuilder = DefaultFileSystemConfigBuilder.getInstance();
            defaultBuilder.setUserAuthenticator(fileSystemOptions, userAuthenticator);
        }
        return fileSystemOptions;
    }

    public void setFileSystemOptions(FileSystemOptions fileSystemOptions) {
        this.fileSystemOptions = fileSystemOptions;
    }

    public FilesCache getFilesCache() {
        if (filesCache == null) {
            filesCache = new SoftRefFilesCache();
        }
        return filesCache;
    }

    public void setFilesCache(FilesCache filesCache) {
        this.filesCache = filesCache;
    }

    public CacheStrategy getCacheStrategy() {
        if (cacheStrategy == null) {
            cacheStrategy = CacheStrategy.ON_CALL;
        }
        return cacheStrategy;
    }

    public void setCacheStrategy(CacheStrategy cacheStrategy) {
        this.cacheStrategy = cacheStrategy;
    }

    public FileSystemManager getFileSystemManager() throws FileSystemException {
        if (fileSystemManager == null) {
            fileSystemManager = new StandardFileSystemManager();
            ((StandardFileSystemManager) fileSystemManager).setFilesCache(getFilesCache());
            ((StandardFileSystemManager) fileSystemManager).setCacheStrategy(getCacheStrategy());
            ((StandardFileSystemManager) fileSystemManager).init();
        }
        return fileSystemManager;
    }

    public void setFileSystemManager(FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setLogger(String loggerName) {
        this.logger = LoggerFactory.getLogger(loggerName);
    }

    public List<String> getAuditMethods() {
        return auditMethods;
    }

    public void setAuditMethods(List<String> auditMethods) {
        this.auditMethods = auditMethods;
    }

    public void setAuditMethods(String auditMethodsStr) {
        this.auditMethods = Arrays.asList(auditMethodsStr.split(","));
    }

    @Override
    public String getInitParameter(String key) {
        String value = super.getInitParameter(key);
        if (value == null && key.equals(INIT_PARAM_AUTHENTICATE_HEADER)) {
            return "Basic realm=\"Webdav VFS\"";
        }
        if (value == null && key.equals(INIT_PARAM_CREATE_ABSOLUTE_URI)) {
            return "false";
        }
        if (value == null && key.equals(INIT_PARAM_CSRF_PROTECTION)) {
            return "disabled";
        }
        return value;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        String message = "Servlet '" + config.getServletName() + "' initialization error";

        String loggerValue = getProperty(config.getInitParameter(INIT_PARAM_LOGGER));
        if (loggerValue != null && !loggerValue.isEmpty()) {
            setLogger(loggerValue);
        }

        String auditMethodsValue = getProperty(config.getInitParameter(INIT_PARAM_AUDMETHODS));
        if (auditMethodsValue != null && !auditMethodsValue.isEmpty()) {
            setAuditMethods(auditMethodsValue);
        }

        String rootpath = getProperty(config.getInitParameter(INIT_PARAM_ROOTPATH));
        if (rootpath == null)
            throw new ServletException(message + ", init parameter '" + INIT_PARAM_ROOTPATH + "' is null");

        if (rootpath.startsWith("./"))
            rootpath = new File(rootpath).getAbsolutePath();

        String listingsDirectoryValue = getProperty(config.getInitParameter(INIT_PARAM_LISTINGS));
        if (listingsDirectoryValue != null)
            listingsDirectory = Boolean.parseBoolean(listingsDirectoryValue);

        String includeContextPathValue = getProperty(config.getInitParameter(INIT_PARAM_INCTXPATH));
        if (includeContextPathValue != null)
            includeContextPath = Boolean.parseBoolean(includeContextPathValue);

        String builderValue = getProperty(config.getInitParameter(INIT_PARAM_BUILDER));
        if (fileSystemOptions == null && builderValue != null) {
            try {
                invokeFileSystemBuilder(builderValue, config);
            } catch (Exception e) {
                throw new ServletException(message, e);
            }
        }

        if (userAuthenticator == null) {
            String domain = getProperty(config.getInitParameter(INIT_PARAM_DOMAIN));
            String login = getProperty(config.getInitParameter(INIT_PARAM_LOGIN));
            String password = getProperty(config.getInitParameter(INIT_PARAM_PASSWORD));
            setUserAuthenticator(new StaticUserAuthenticator(domain, login, password));
        }

        String filesCacheValue = getProperty(config.getInitParameter(INIT_PARAM_FILESCACHE));
        if (filesCache == null && filesCacheValue != null) {
            try {
                Class<?> filesCacheClass = Class.forName(filesCacheValue);
                setFilesCache((FilesCache) filesCacheClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new ServletException(message, e);
            }
        }

        String cacheStrategyValue = getProperty(config.getInitParameter(INIT_PARAM_CACHESTRATEGY));
        if (cacheStrategy == null && cacheStrategyValue != null) {
            if ("manual".equals(cacheStrategyValue))
                setCacheStrategy(CacheStrategy.MANUAL);
            else if ("onresolve".equals(cacheStrategyValue))
                setCacheStrategy(CacheStrategy.ON_RESOLVE);
            else if ("oncall".equals(cacheStrategyValue))
                setCacheStrategy(CacheStrategy.ON_CALL);
            else
                throw new ServletException(message + ", cache strategy '" + cacheStrategyValue + "' not valid");
        }

        try {
            fileObject = getFileSystemManager().resolveFile(rootpath, getFileSystemOptions());
            if (fileObject == null || !fileObject.exists())
                throw new ServletException(message + ", VFS root file not exist or null");
        } catch (FileSystemException e) {
            throw new ServletException(message, e);
        }

        logger.info("Init servlet: {}, rootpath: {}, listingsDirectory: {}, version: {}", config.getServletName(),
                fileObject.getPublicURIString(), listingsDirectory, VERSION);
        super.init(config);
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            // Include servlet path to prefix
            if (includeContextPath) {
                HttpServletRequest wrapper = new HttpServletRequestWrapper(request) {
                    @Override
                    public String getContextPath() {
                        return request.getContextPath() + request.getServletPath();
                    }
                };
                super.service(wrapper, response);
            } else {
                super.service(request, response);
            }
        } finally {
            if (auditMethods != null && auditMethods.contains(request.getMethod())) {
                String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName()
                        : request.getRemoteUser();
                String id = request.getSession(false) != null ? request.getSession(false).getId() : null;
                String requestURI = Text.unescape(request.getRequestURI());
                String dest = request.getHeader("Destination");
                dest = (dest != null) ? Text.unescape(dest.replaceAll("^http.*://[^/]*+", "")) : null;
                String msg = response.getStatus() + " " + request.getMethod() + " : " + requestURI
                        + (dest != null ? ", Destination: " + dest : "") + ", User: " + user + ", ID: " + id
                        + ", Addr: " + request.getRemoteAddr();
                logger.info(msg);
            }
        }
    }

    @Override
    protected void doGet(WebdavRequest request, WebdavResponse response, DavResource resource)
            throws IOException, DavException {
        if (listingsDirectory && resource.exists() && resource.isCollection()) {
            printDirectory(request, response, resource);
            return;
        }
        super.doGet(request, response, resource);
    }

    @Override
    public void destroy() {
        logger.info("Destroy servlet: {}, rootpath: {}, listingsDirectory: {}, version: {}",
                getServletConfig() != null ? getServletName() : this,
                fileObject != null ? fileObject.getPublicURIString() : fileObject, listingsDirectory, VERSION);
        if (fileSystemManager != null) {
            if (fileObject != null) {
                fileSystemManager.closeFileSystem(fileObject.getFileSystem());
            }
            fileSystemManager.close();
        }
        super.destroy();
    }

    protected String getProperty(String key) {
        if (key != null && key.startsWith("${") && key.endsWith("}")) {
            key = System.getProperty(key.substring(2, key.length() - 1));
        }
        return key;
    }

    private void invokeFileSystemBuilder(String builderValue, ServletConfig config)
            throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Class<?> builderClass = Class.forName(builderValue);
        Object builder = builderClass.getMethod("getInstance").invoke(null);
        fileSystemOptions = new FileSystemOptions();

        List<String> keys = Collections.list(config.getInitParameterNames());
        for (String key : keys) {
            if (key != null && key.startsWith(INIT_PARAM_BUILDER + ".")) {
                String name = key.replaceAll("^" + INIT_PARAM_BUILDER + "\\.", "");
                name = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
                Method m;
                String param = getProperty(config.getInitParameter(key));
                Object value = param;
                if ("true".equalsIgnoreCase(param) || "false".equalsIgnoreCase(param)) {
                    value = Boolean.valueOf(param);
                    try {
                        m = builderClass.getMethod(name, FileSystemOptions.class, boolean.class);
                    } catch (NoSuchMethodException e) {
                        m = builderClass.getMethod(name, FileSystemOptions.class, Boolean.class);
                    }
                } else {
                    try {
                        value = Integer.parseInt(param);
                        try {
                            m = builderClass.getMethod(name, FileSystemOptions.class, int.class);
                        } catch (NoSuchMethodException e) {
                            m = builderClass.getMethod(name, FileSystemOptions.class, Integer.class);
                        }
                    } catch (NumberFormatException e) {
                        try {
                            value = Long.parseLong(param);
                            try {
                                m = builderClass.getMethod(name, FileSystemOptions.class, long.class);
                            } catch (NoSuchMethodException nsme) {
                                m = builderClass.getMethod(name, FileSystemOptions.class, Long.class);
                            }
                        } catch (NumberFormatException e1) {
                            try {
                                m = builderClass.getMethod(name, FileSystemOptions.class, String.class);
                            } catch (NoSuchMethodException nsme) {
                                m = builderClass.getMethod(name, FileSystemOptions.class, Object.class);
                            }
                        }
                    }
                }

                logger.trace("# builder: {}, {}, {}, {}", key, name, value, value.getClass());
                m.invoke(builder, fileSystemOptions, value);
            }
        }
    }

    private static final DateFormat shortDF = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
    private static final String CSS;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("h1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} ");
        sb.append("h2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} ");
        sb.append("h3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} ");
        sb.append("body {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} ");
        sb.append("b {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} ");
        sb.append("p {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;} ");
        sb.append("a {color:black;} a.name {color:black;} ");
        sb.append(".line {height:1px;background-color:#525D76;border:none;}");
        CSS = sb.toString();
    }

    protected void printDirectory(WebdavRequest request, WebdavResponse response, DavResource resource)
            throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("<html>");
        sb.append("<head>");
        sb.append("<title>" + getServletConfig().getServletName() + "</title>");
        sb.append("<link rel=\"SHORTCUT ICON\" href=\"data:image/png;base64,XXXXX\">");
        sb.append("<style type=\"text/css\">");
        sb.append(getCSS());
        sb.append("</style>");
        sb.append("</head>");
        sb.append("<body>");

        String resourcePath = resource.getResourcePath().endsWith("/") ? resource.getResourcePath()
                : resource.getResourcePath() + "/";
        sb.append("<h2>Content of folder: " + resourcePath + "</h2>");

        sb.append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\" align=\"center\">\r\n");

        final String tdHeadStart = "<td align=\"left\"><font size=\"+1\"><strong>";
        final String tdHeadEnd = "</strong></font></td>\r\n";

        // Head table
        sb.append("<tr>\r\n");
        sb.append(tdHeadStart);
        sb.append("Name");
        sb.append(tdHeadEnd);
        sb.append(tdHeadStart);
        sb.append("Size");
        sb.append(tdHeadEnd);
        sb.append(tdHeadStart);
        sb.append("Type");
        sb.append(tdHeadEnd);
        sb.append(tdHeadStart);
        sb.append("Modified");
        sb.append(tdHeadEnd);
        sb.append("</tr>");

        sb.append("<tr><td colspan=\"4\"><a href=\"../\"><tt>[Parent]</tt></a></td></tr>");

        String baseDir = request.getRequestURI().endsWith("/") ? request.getRequestURI()
                : request.getRequestURI() + "/";

        boolean isEven = false;
        Iterator<DavResource> resources = resource.getMembers();

        // Sort list
        ArrayList<DavResource> list = new ArrayList<>();
        resources.forEachRemaining(list::add);

        Collections.sort(list, (d1, d2) -> {
            return d1.getDisplayName().compareTo(d2.getDisplayName());
        });

        for (DavResource res : list) {
            isEven = !isEven;
            long lastModified = res.getModificationTime();
            boolean isDir = res.isCollection();
            String name = !isDir ? res.getDisplayName() : res.getDisplayName() + "/";

            // Striped table
            sb.append("<tr " + (isEven ? "bgcolor=\"#eeeeee\"" : "") + "\">");

            // Name column
            sb.append("<td>").append("<a href=\"");
            sb.append(baseDir + name);
            sb.append("\"><tt>");
            sb.append(name);
            sb.append("</tt></a></td>");

            final String tdStart = "<td><tt>";
            final String tdEnd = "</tt></td>";

            // Size column
            if (isDir) {
                sb.append(tdStart);
                sb.append("Folder");
                sb.append(tdEnd);
            } else {
                DavProperty<?> prop = res.getProperty(DavPropertyName.GETCONTENTLENGTH);
                String value = prop != null ? (String) prop.getValue() : null;
                long length = value != null ? Long.valueOf(value) : 0L;
                sb.append(tdStart).append(renderSize(length)).append(tdEnd);
            }

            // MIME type column
            if (isDir) {
                sb.append(tdStart);
                sb.append("-");
                sb.append(tdEnd);
            } else {
                sb.append(tdStart);
                DavProperty<?> prop = res.getProperty(DavPropertyName.GETCONTENTTYPE);
                String mimeType = prop != null ? (String) prop.getValue() : "Unknown type";
                sb.append(mimeType);
                sb.append(tdEnd);
            }

            // Date column
            sb.append(tdStart);
            sb.append(shortDF.format(lastModified));
            sb.append(tdEnd);

            sb.append("</tr>");
        }

        sb.append("</table>");
        sb.append("<h3 id=\"version\" value=\"" + VERSION + "\">Application version: " + VERSION + "</h3>");
        sb.append("</body></html>");

        response.setContentType("text/html; charset=UTF-8");
        response.getWriter().print(sb.toString());
    }

    protected String getCSS() {
        return CSS;
    }

    protected String renderSize(long size) {
        long leftSide = size / 1024;
        long rightSide = (size % 1024) / 103; // Makes 1 digit
        if ((leftSide == 0) && (rightSide == 0) && (size > 0))
            rightSide = 1;
        return ("" + leftSide + "." + rightSide + " kb");
    }
}
