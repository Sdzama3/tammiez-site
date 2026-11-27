package com.teccalls.recorder

import java.io.File
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * Sends one recording as an email attachment through Gmail's SMTP server.
 * Uses the user's own Gmail address and a Google "App Password" that they
 * entered in Settings. Credentials never leave the device except to Gmail.
 * Call this OFF the main thread.
 */
object MailSender {

    fun send(
        senderEmail: String,
        appPassword: String,
        recipient: String,
        subject: String,
        body: String,
        attachment: File
    ) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(senderEmail, appPassword)
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(senderEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
            setSubject(subject)
        }

        val textPart = MimeBodyPart().apply { setText(body) }
        val filePart = MimeBodyPart().apply { attachFile(attachment) }

        message.setContent(MimeMultipart().apply {
            addBodyPart(textPart)
            addBodyPart(filePart)
        })

        Transport.send(message)
    }
}
