/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.launcher.daemon.client

import org.gradle.api.specs.Spec
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.diagnostics.DaemonProcessInfo
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry
import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.OutgoingConnector
import spock.lang.Specification

class DefaultDaemonConnectorTest extends Specification {

    def javaHome = new File("tmp")
    def connectTimeoutSecs = 1
    def daemonCounter = 0

    def createOutgoingConnector() {
        new OutgoingConnector() {
            Connection connect(Address address) {
                def connection = [:] as Connection
                // unsure why I can't add this as property in the map-mock above
                connection.metaClass.num = address.num
                connection
            }
        }
    }

    def createAddress(int i) {
        new Address() {
            int getNum() { i }

            String getDisplayName() { getNum() }
        }
    }

    def createConnector() {
        def connector = new DefaultDaemonConnector(
                new EmbeddedDaemonRegistry(),
                createOutgoingConnector(),
                { startBusyDaemon() } as DaemonStarter
        )
        connector.connectTimeout = connectTimeoutSecs * 1000
        connector
    }

    def startBusyDaemon() {
        def daemonNum = daemonCounter++
        DaemonContext context = new DefaultDaemonContext(daemonNum.toString(), javaHome, javaHome, daemonNum, 1000, [])
        def address = createAddress(daemonNum)
        registry.store(address, context, "password")
        registry.markBusy(address)
        return new DaemonProcessInfo(daemonNum.toString(), null);
    }

    def startIdleDaemon() {
        def daemonNum = daemonCounter++
        DaemonContext context = new DefaultDaemonContext(daemonNum.toString(), javaHome, javaHome, daemonNum, 1000, [])
        def address = createAddress(daemonNum)
        registry.store(address, context, "password")
    }

    def theConnector

    def getConnector() {
        if (theConnector == null) {
            theConnector = createConnector()
        }
        theConnector
    }

    def getRegistry() {
        connector.daemonRegistry
    }

    def getNumAllDaemons() {
        registry.all.size()
    }

    def "maybeConnect() returns connection to any daemon that matches spec"() {
        given:
        startIdleDaemon()
        startIdleDaemon()
        
        expect:
        def connection = connector.maybeConnect({it.pid < 12} as Spec)
        connection && connection.connection.num < 12
    }

    def "maybeConnect() returns null when no daemon matches spec"() {
        given:
        startIdleDaemon()
        startIdleDaemon()

        expect:
        connector.maybeConnect({it.pid == 12} as Spec) == null
    }

    def "maybeConnect() ignores daemons that do not match spec"() {
        given:
        startIdleDaemon()
        startIdleDaemon()

        expect:
        def connection = connector.maybeConnect({it.pid == 1} as Spec)
        connection && connection.connection.num == 1
    }

    def "connect() returns connection to any existing daemon that matches spec"() {
        given:
        startIdleDaemon()
        startIdleDaemon()

        expect:
        def connection = connector.connect({it.pid < 12} as Spec)
        connection && connection.connection.num < 12

        and:
        numAllDaemons == 2
    }

    def "connect() starts a new daemon when no daemon matches spec"() {
        given:
        startIdleDaemon()

        expect:
        def connection = connector.connect({it.pid > 0} as Spec)
        connection && connection.connection.num > 0

        and:
        numAllDaemons == 2
    }

    def "connect() will not use existing connection if it fails the compatibility spec"() {
        given:
        startIdleDaemon()

        expect:
        def connection = connector.connect({it.pid != 0} as Spec)
        connection && connection.connection.num != 0

        and:
        numAllDaemons == 2
    }

    def "connect() will use daemon started by connector even if it fails compatibility spec"() {
        when:
        def connection = connector.connect({false} as Spec)
        connection && connection.connection.num == 0

        then:
        numAllDaemons == 1
    }


}