[![Build Status](https://travis-ci.org/a-langer/webdav-vfs-gate.svg?branch=master)](https://travis-ci.org/a-langer/webdav-vfs-gate)
[![license](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/a-langer/webdav-vfs-gate/blob/master/LICENSE)
[![Maven JitPack](https://img.shields.io/github/tag/a-langer/webdav-vfs-gate.svg?label=maven)](https://jitpack.io/#a-langer/webdav-vfs-gate)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.a-langer/webdav-vfs-gate/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.a-langer/webdav-vfs-gate)

# WebDAV VFS gate

This project implement WebDAV gateway for accessing to different file systems. The file systems access level is based on the [Apache Commons VFS][1] library. WebDAV protocol layer is based on [Apache Jackrabbit][2] library.

## Supported features

* Available [file systems][3] of Apache Commons VFS  (smb,ftp,sftp,http,webdav,zip,jar and other).
* WebDAV compatible with [Windows Explorer][4], [GVFS][5] and [davfs2][6].
* Server implemented as library (jar) and web application (war).
* Application ready for use in web containers, such as [Tomcat][7], [Jetty][8], [JBoss][9] and similar.
* Configuring file system from servlet initialization parameters and java properties.
* Audit log of file operations.

## Initialization parameters

* `rootpath` - connection string for file system ([see more example][3]). Parameter must be specified.
* `login` - connection login for file system, optional parameter.
* `password` - connection password for file system, optional parameter.
* `domain` - connection domain for file system, optional parameter.
* `listings-directory` - boolean parameter, enables showing directory content as html page, by default is `true`.
* `include-context-path` - boolean parameter, enables containing context path in resource path, by default is `true`.
* `files-cache` - class full name of [Apache VFS][10] cache implementation, by default is `org.apache.commons.vfs2.cache.SoftRefFilesCache`.
* `cache-strategy` - name of [Apache VFS][11] cache strategy, may take values: `manual`, `onresolve` or `oncall`. By default is `oncall`.
* `builder` - class full name of [Apache VFS][1] file system config builder, specific to each file system, ex.: for FTP is `org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder`.
* `builder.<method_name>` - string parameter determine method name for invoke in instance of file system config builder. To call  setters needs convert method name to property name, ex.: method `setControlEncoding` must be converted to `controlEncoding`. Value of parameter may be string, integer or boolean (see example in [web.xml](./web.xml#L79-L98)).
* `logger-name` - name for servlet logger, by default is `com.github.alanger.webdav.VfsWebDavServlet`.
* `audit-methods` - a comma-separated list of http methods for file operations audit logs, optional parameter.
* `createAbsoluteURI` - boolean parameter, enables using an absolute URI instead of a relative, by default is `false`.
* `csrf-protection` - configuration of the CSRF protection, may contain a comma-separated list of allowed referrer hosts. By default is `disabled`.

## Usage

Deploy [webdav-vfs-gate-<version>.war][13] to servlets container ([Tomcat][7], [Jetty][8], [JBoss][9] or similar).
Add servlet declarations to `web.xml` (see documentation of servlet container).  

Example for local file system:

```xml
<servlet>
    <servlet-name>root</servlet-name>
    <servlet-class>com.github.alanger.webdav.VfsWebDavServlet</servlet-class>
<init-param>
    <param-name>rootpath</param-name>
    <param-value>/path/to/filesystem/folder</param-value>
</init-param>
</servlet>
<servlet-mapping>
    <servlet-name>root</servlet-name>
    <url-pattern>/root/*</url-pattern>
</servlet-mapping>
```

Example for SMB file system with login and password in connection string:

```xml
<servlet>
    <servlet-name>smb</servlet-name>
    <servlet-class>com.github.alanger.webdav.VfsWebDavServlet</servlet-class>
    <init-param>
        <param-name>rootpath</param-name>
        <param-value>smb://DOMAIN\mylogin:mypassword@hostname:445/path</param-value>
    </init-param>
</servlet>
<servlet-mapping>
    <servlet-name>smb</servlet-name>
    <url-pattern>/smb/*</url-pattern>
</servlet-mapping>
```

Example for SMB file system with login and password in initialize parameters:

```xml
<servlet>
    <servlet-name>smb</servlet-name>
    <servlet-class>com.github.alanger.webdav.VfsWebDavServlet</servlet-class>
    <init-param>
        <param-name>rootpath</param-name>
        <param-value>smb://hostname:445/path</param-value>
    </init-param>
    <init-param>
        <param-name>domain</param-name>
        <param-value>DOMAIN</param-value>
    </init-param>
    <init-param>
        <param-name>login</param-name>
        <param-value>mylogin</param-value>
    </init-param>
    <init-param>
        <param-name>password</param-name>
        <param-value>mypassword</param-value>
    </init-param>
</servlet>
<servlet-mapping>
    <servlet-name>smb</servlet-name>
    <url-pattern>/smb/*</url-pattern>
</servlet-mapping>
```

Example for SMB file system with connection string from system properties:

```bash
-Dsmb.connection.string="smb://DOMAIN\mylogin:mypassword@hostname:445/path"
```

```xml
<servlet>
    <servlet-name>smb</servlet-name>
    <servlet-class>com.github.alanger.webdav.VfsWebDavServlet</servlet-class>
    <init-param>
        <param-name>rootpath</param-name>
        <param-value>${smb.connection.string}</param-value>
    </init-param>
</servlet>
<servlet-mapping>
    <servlet-name>smb</servlet-name>
    <url-pattern>/smb/*</url-pattern>
</servlet-mapping>
```

Example for file system with audit log of file operations:

```bash
-Dlogback.configurationFile=/path/to/config/logback.xml
```

```xml
<!-- In logback.xml -->
<logger name="com.github.alanger.webdav.my_audit_logger" level="INFO" additivity="false">
    <appender-ref ref="STDOUT" />
</logger>
```

```xml
<servlet>
    <servlet-name>audit</servlet-name>
    <servlet-class>com.github.alanger.webdav.VfsWebDavServlet</servlet-class>
    <init-param>
        <param-name>rootpath</param-name>
        <param-value>/path/to/filesystem/folder</param-value>
    </init-param>
    <init-param>
        <param-name>logger-name</param-name>
        <param-value>com.github.alanger.webdav.my_audit_logger</param-value>
    </init-param>
    <init-param>
        <param-name>audit-methods</param-name>
        <param-value>GET,MKCOL,DELETE,COPY,MOVE,PUT,PROPPATH</param-value>
    </init-param>
</servlet>
<servlet-mapping>
    <servlet-name>audit</servlet-name>
    <url-pattern>/audit/*</url-pattern>
</servlet-mapping>
```

Example for MIME types configuration (see [content-types.properties](./content-types.properties) file):

```bash
-Dcontent.types.user.table=/path/to/config/content-types.properties
```

## More examples
Servlet configurations see in [web.xml](./web.xml) file.  
File systems see in [Apache Commons VFS][3] documentation.  
Logger configuration see in [Logback][12] documentation.  

## Getting the library using Maven

Add this dependency to your `pom.xml` to reference the library:

```xml
<dependency>
    <groupId>com.github.a-langer</groupId>
    <artifactId>webdav-vfs-gate</artifactId>
    <version>1.0.0</version>
    <classifier>classes</classifier>
</dependency>
```

## Related repositories

* [tomcat-cache-realm](https://github.com/shopping24/tomcat-cache-realm) - Cache to authentication realm in Tomcat.
* [hazelcast-tomcat-sessionmanager](https://github.com/hazelcast/hazelcast-tomcat-sessionmanager) - Hazelcast Tomcat Session Manager.
* [tomcat-vault](https://github.com/web-servers/tomcat-vault) - Vault for Apache Tomcat.

[1]: https://commons.apache.org/proper/commons-vfs/index.html
[2]: https://jackrabbit.apache.org/jcr/components/jackrabbit-webdav-library.html
[3]: https://commons.apache.org/proper/commons-vfs/filesystems.html
[4]: https://docs.microsoft.com/en-us/windows/win32/webdav/webdav-portal
[5]: https://wiki.gnome.org/Projects/gvfs
[6]: https://savannah.nongnu.org/projects/davfs2
[7]: http://tomcat.apache.org/
[8]: https://www.eclipse.org/jetty/
[9]: https://www.jboss.org/
[10]: https://commons.apache.org/proper/commons-vfs/api.html#Cache
[11]: https://cwiki.apache.org/confluence/display/COMMONS/VfsCacheStrategy
[12]: http://logback.qos.ch/manual/configuration.html
[13]: https://github.com/a-langer/webdav-vfs-gate/releases
