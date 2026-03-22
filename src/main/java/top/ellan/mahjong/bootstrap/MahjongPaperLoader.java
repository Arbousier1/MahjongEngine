package top.ellan.mahjong.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public final class MahjongPaperLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
            "central",
            "default",
            MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());

        this.addDependency(resolver, "io.github.ssttkkl:mahjong-utils-jvm:0.7.7");
        this.addDependency(resolver, "org.mariadb.jdbc:mariadb-java-client:3.5.3");
        this.addDependency(resolver, "com.h2database:h2:2.3.232");
        this.addDependency(resolver, "com.zaxxer:HikariCP:6.3.0");
        this.addDependency(resolver, "org.jetbrains.kotlin:kotlin-stdlib:2.2.0");
        this.addDependency(resolver, "org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0");

        classpathBuilder.addLibrary(resolver);
    }

    private void addDependency(MavenLibraryResolver resolver, String coordinates) {
        resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
    }
}

