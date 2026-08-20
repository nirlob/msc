package io.msc.config;

import io.msc.service.ContributionService;
import io.msc.storage.MscRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

/**
 * Beans for the persistence, repository and service layers.
 *
 * `msc.db.path` defaults to "./msc.db". Set to ":memory:" for tests.
 */
@Configuration
public class MscConfig {

    @Bean
    public DataSource dataSource(@Value("${msc.db.path:./msc.db}") String path) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + path);
        ds.setEnforceForeignKeys(true);
        return ds;
    }

    @Bean
    public MscRepository mscRepository(DataSource ds) {
        return new MscRepository(ds);
    }

    @Bean
    public ContributionService contributionService(MscRepository repo) {
        return new ContributionService(repo);
    }
}