package cn.suhoan.starlight.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

public class HibernateLoggingRuntimeHints implements RuntimeHintsRegistrar {

    private static final String HIBERNATE_LOGGER_CLASS_PATTERN = "classpath*:org/hibernate/**/*_$logger.class";
    private static final String HIBERNATE_LISTENER_CLASS_PATTERN = "classpath*:org/hibernate/**/*Listener.class";
    private static final String FLYWAY_CONFIGURATION_EXTENSION_PATTERN =
            "classpath*:org/flywaydb/core/internal/configuration/extensions/*.class";
    private static final String HIBERNATE_LOGGER_RESOURCE_PATTERN = "org/hibernate/**/*.i18n.properties";
    private static final String FLYWAY_MIGRATION_ROOT_PATTERN = "db/migration";
    private static final String FLYWAY_COMMON_MIGRATION_DIRECTORY_PATTERN = "db/migration/common";
    private static final String FLYWAY_COMMON_MIGRATION_RESOURCE_PATTERN = "db/migration/common/*.sql";
    private static final String FLYWAY_VENDOR_MIGRATION_DIRECTORY_PATTERN = "db/migration/*";
    private static final String FLYWAY_VENDOR_MIGRATION_RESOURCE_PATTERN = "db/migration/*/*.sql";
    private static final String ROOT_PACKAGE_PREFIX = "org/";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        hints.resources().registerPattern(HIBERNATE_LOGGER_RESOURCE_PATTERN);
        hints.resources().registerPattern(FLYWAY_MIGRATION_ROOT_PATTERN);
        hints.resources().registerPattern(FLYWAY_COMMON_MIGRATION_DIRECTORY_PATTERN);
        hints.resources().registerPattern(FLYWAY_COMMON_MIGRATION_RESOURCE_PATTERN);
        hints.resources().registerPattern(FLYWAY_VENDOR_MIGRATION_DIRECTORY_PATTERN);
        hints.resources().registerPattern(FLYWAY_VENDOR_MIGRATION_RESOURCE_PATTERN);
        hints.resources().registerPattern("logback.xml");
        try {
            registerHibernateLoggerHints(hints, resolver);
            registerHibernateListenerArrayHints(hints, resolver);
            registerFlywayConfigurationExtensionHints(hints, resolver);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to register native runtime hints", ex);
        }
    }

    private void registerHibernateLoggerHints(RuntimeHints hints, PathMatchingResourcePatternResolver resolver) throws IOException {
        Resource[] resources = resolver.getResources(HIBERNATE_LOGGER_CLASS_PATTERN);
        for (Resource resource : resources) {
            hints.reflection().registerType(TypeReference.of(toClassName(resource)),
                    builder -> builder.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        }
    }

    private void registerHibernateListenerArrayHints(RuntimeHints hints, PathMatchingResourcePatternResolver resolver) throws IOException {
        Resource[] resources = resolver.getResources(HIBERNATE_LISTENER_CLASS_PATTERN);
        for (Resource resource : resources) {
            String className = toClassName(resource);
            if (!className.contains("$")) {
                hints.reflection().registerType(TypeReference.of(className + "[]"));
            }
        }
    }

    private void registerFlywayConfigurationExtensionHints(RuntimeHints hints,
                                                           PathMatchingResourcePatternResolver resolver) throws IOException {
        Resource[] resources = resolver.getResources(FLYWAY_CONFIGURATION_EXTENSION_PATTERN);
        for (Resource resource : resources) {
            String className = toClassName(resource);
            if (!className.endsWith(".")) {
                hints.reflection().registerType(TypeReference.of(className),
                        builder -> builder.withMembers(
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_METHODS
                        ));
            }
        }
    }

    private String toClassName(Resource resource) throws IOException {
        String path = resource.getURL().toString();
        int jarSeparatorIndex = path.indexOf("!/");
        if (jarSeparatorIndex >= 0) {
            path = path.substring(jarSeparatorIndex + 2);
        }
        int packagePathIndex = path.indexOf(ROOT_PACKAGE_PREFIX);
        if (packagePathIndex < 0) {
            throw new IllegalStateException("Unexpected runtime hint resource path: " + path);
        }
        String classPath = path.substring(packagePathIndex);
        return classPath.substring(0, classPath.length() - ".class".length()).replace('/', '.');
    }
}
