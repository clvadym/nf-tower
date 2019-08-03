/*
 * Copyright (c) 2019, Seqera Labs.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.tower.service

import javax.inject.Inject
import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart
import javax.validation.ValidationException
import java.time.Instant

import grails.gorm.transactions.TransactionService
import grails.gorm.transactions.Transactional
import io.micronaut.context.annotation.Value
import io.micronaut.test.annotation.MicronautTest
import io.seqera.mail.MailerConfig
import io.seqera.tower.Application
import io.seqera.tower.domain.AccessToken
import io.seqera.tower.domain.User
import io.seqera.tower.domain.UserRole
import io.seqera.tower.exchange.gate.AccessGateResponse
import io.seqera.tower.util.AbstractContainerBaseTest
import io.seqera.tower.util.DomainCreator
import org.subethamail.wiser.Wiser
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(application = Application.class)
@Transactional
class GateServiceTest extends AbstractContainerBaseTest {

    @Inject
    MailerConfig mailerConfig

    @Inject
    GateService gateService

    @Inject
    TransactionService tx

    @Value('${tower.contact-email}')
    String contactEmail

    Wiser smtpServer

    void setup() {
        smtpServer = new Wiser(mailerConfig.smtp.port)
        smtpServer.start()
    }

    void cleanup() {
        smtpServer.stop()
    }

    void "a new user is created on first time access"() {
        given: "an email"
        String EMAIL = 'tomas@seqera.io'

        when: "the user send an access request"
        AccessGateResponse resp = tx.withNewTransaction { gateService.access(EMAIL) }

        then: "the user has been created"
        resp.user.id
        resp.user.email == EMAIL
        resp.user.userName == EMAIL.replaceAll(/@.*/, '')
        and:
        !resp.user.trusted
        and:
        !resp.user.authToken
        !resp.user.authTime
        and:
        User.withNewTransaction { User.count() } == 1

        and: "the user access need to be approved"
        resp.state == AccessGateResponse.State.PENDING_APPROVAL
        
        and: "the access token has been created"
        resp.user.accessTokens.size() == 1
        AccessToken.withNewTransaction { AccessToken.count() } == 1

        and: "a role was attached to the user"
        UserRole.list().first().user.id == resp.user.id
        UserRole.list().first().role.authority == 'ROLE_USER'

        and: "the new user registration has been notified to webmaster"
        smtpServer.messages.size() == 1
        Message message = smtpServer.messages.first().mimeMessage
        message.allRecipients.contains(new InternetAddress(contactEmail))
    }

    void "a new user is created as trusted" () {
        given:
        def EMAIL = 'me@hack.com'

        when: "the user send an access request"
        AccessGateResponse resp = tx.withNewTransaction { gateService.access(EMAIL) }

        then: "the user has been created"
        resp.user.id
        resp.user.email == EMAIL
        resp.user.userName == EMAIL.replaceAll(/@.*/, '')
        and:
        resp.user.trusted
        and:
        resp.user.authToken
        resp.user.authTime
        and:
        User.withNewTransaction { User.count() } == 1

        and:
        resp.state == AccessGateResponse.State.LOGIN_ALLOWED

        and: "the new user registration has been notified to webmaster"
        smtpServer.messages.size() == 1
        Message message = smtpServer.messages.first().mimeMessage
        message.allRecipients.contains(new InternetAddress(EMAIL))
    }

    void "a trusted user try to access"() {
        given: "an existing user"
        User user = new DomainCreator().createUser(trusted: true)

        and: 'save the authToken and authTime for a later check'
        String authToken = user.authToken
        Instant authTime = user.authTime

        when: "the user send an access request"
        def resp = tx.withNewTransaction { gateService.access(user.email) }
        String userName = resp.user.userName

        then:
        resp.state == AccessGateResponse.State.LOGIN_ALLOWED
        
        and: "the returned user is the same as the previous one"
        resp.user.id == user.id
        resp.user.email == user.email
        tx.withNewTransaction { User.count() } == 1

        and: 'the auth token has been refreshed'
        resp.user.authToken != authToken
        resp.user.authTime > authTime

        and: 'the resp email has been sent'
        smtpServer.messages.size() == 1
        Message message = smtpServer.messages.first().mimeMessage
        message.allRecipients.contains(new InternetAddress(user.email))
        message.subject == 'Nextflow Tower Sign in'
        (message.content as MimeMultipart).getBodyPart(0).content.getBodyPart(0).content.contains("Hi $userName,")
    }

    def 'a not trusted user repeat the access' () {
        given: "an existing user"
        User user = new DomainCreator().createUser(trusted: false, email: 'foo@gmail.com')

        when: "the user send an access request"
        def resp = tx.withNewTransaction { gateService.access(user.email) }

        then:
        resp.state == AccessGateResponse.State.KEEP_CALM_PLEASE

        and: "the returned user is the same as the previous one"
        resp.user.id == user.id
        resp.user.email == user.email
        tx.withNewTransaction { User.count() } == 1

        and: 'no email was sent'
        smtpServer.messages.size() == 0
    }

    void "register a new user and then a user with a similar email"() {
        when: "register a user"
        String email = 'user@seqera.io'

        User user = tx.withNewTransaction { gateService.access('user@seqera.io') }.user

        then: "the user has been created"
        user.id
        user.email == email
        user.userName == email.replaceAll(/@.*/, '')
        !user.authToken
        !user.trusted
        tx.withNewTransaction { User.count() } == 1

        when: "register a user with a similar email to the first one"
        String email2 = 'user@email.com'
        User user2 = tx.withNewTransaction { gateService.access(email2) }.user

        then: "the user has been created and the userName has an appended number"
        user2.id
        user2.email == email2
        user2.userName == 'user1'
        !user2.authToken
        !user2.trusted
        tx.withNewTransaction { User.count() } == 2
    }

    void "try to register a user given an invalid email"() {
        given: "an invalid email"
        String badEmail = 'badEmail'

        when: "register a user with a bad email"
        gateService.access(badEmail)

        then: "the user couldn't be created"
        ValidationException e = thrown(ValidationException)
        e.message == "Can't save a user with bad email format"
        tx.withNewTransaction {
            User.count() == 0
        }
    }

}