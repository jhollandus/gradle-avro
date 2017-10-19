package com.github.jholland.gradle.avro

import org.gradle.api.GradleException
import spock.lang.Specification

class ExceptionsSpec extends Specification {

    void 'Exception thrown as GradleException'() {
        when:
        Exceptions.asGradleException(new Exceptions.UncheckRun() {
            @Override
            void run() throws Exception {
                throw new Exception('BOOM')
            }
        })

        then:
        thrown(GradleException)
    }
}
