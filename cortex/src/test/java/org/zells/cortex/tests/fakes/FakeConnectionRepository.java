package org.zells.cortex.tests.fakes;

import org.zells.dish.network.connecting.Connection;
import org.zells.dish.network.connecting.ConnectionRepository;

import java.io.IOException;

public class FakeConnectionRepository extends ConnectionRepository {
    public FakeConnectionRepository() {
        add(new FakeConnectionFactory());
    }

    private class FakeConnectionFactory implements ConnectionFactory {
        @Override
        public boolean canBuild(String description) {
            return true;
        }

        @Override
        public Connection build(String description) throws IOException {
            return new FakeConnection(description);
        }
    }
}
