/**
 * Copyright (C) 2015-2017 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.as4.client;

import java.io.File;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.mail.internet.MimeMessage;

import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.helger.as4.CAS4;
import com.helger.as4.attachment.EAS4CompressionMode;
import com.helger.as4.attachment.WSS4JAttachment;
import com.helger.as4.crypto.AS4CryptoFactory;
import com.helger.as4.messaging.domain.AS4UserMessage;
import com.helger.as4.messaging.domain.CreateUserMessage;
import com.helger.as4.messaging.domain.MessageHelperMethods;
import com.helger.as4.messaging.encrypt.EncryptionCreator;
import com.helger.as4.messaging.mime.MimeMessageCreator;
import com.helger.as4.messaging.sign.SignedMessageCreator;
import com.helger.as4.util.AS4ResourceManager;
import com.helger.as4.util.AS4XMLHelper;
import com.helger.as4lib.ebms3header.Ebms3CollaborationInfo;
import com.helger.as4lib.ebms3header.Ebms3MessageInfo;
import com.helger.as4lib.ebms3header.Ebms3MessageProperties;
import com.helger.as4lib.ebms3header.Ebms3PartyInfo;
import com.helger.as4lib.ebms3header.Ebms3PayloadInfo;
import com.helger.as4lib.ebms3header.Ebms3Property;
import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.ext.CommonsArrayList;
import com.helger.commons.collection.ext.CommonsLinkedHashMap;
import com.helger.commons.collection.ext.ICommonsList;
import com.helger.commons.collection.ext.ICommonsMap;
import com.helger.commons.mime.IMimeType;
import com.helger.commons.string.StringHelper;

/**
 * AS4 standalone client invoker.
 *
 * @author Philip Helger
 * @author bayerlma
 */
@NotThreadSafe
public class AS4Client extends AbstractAS4Client
{
  private final AS4ResourceManager m_aResMgr;

  private Node m_aPayload;
  private final ICommonsList <WSS4JAttachment> m_aAttachments = new CommonsArrayList <> ();

  // Document related attributes
  private final ICommonsList <Ebms3Property> m_aEbms3Properties = new CommonsArrayList <> ();

  // CollaborationInfo
  private String m_sAction;

  private String m_sServiceType;
  private String m_sServiceValue;

  private String m_sConversationID;

  private String m_sAgreementRefPMode;
  private String m_sAgreementRefValue;

  private String m_sFromRole = CAS4.DEFAULT_ROLE;
  private String m_sFromPartyID;

  private String m_sToRole = CAS4.DEFAULT_ROLE;
  private String m_sToPartyID;

  public AS4Client ()
  {
    this (new AS4ResourceManager ());
  }

  public AS4Client (@Nonnull final AS4ResourceManager aResMgr)
  {
    ValueEnforcer.notNull (aResMgr, "ResMgr");
    m_aResMgr = aResMgr;
  }

  @Nonnull
  protected AS4ResourceManager getResourceMgr ()
  {
    return m_aResMgr;
  }

  private void _checkMandatoryAttributes ()
  {
    if (StringHelper.hasNoText (m_sAction))
      throw new IllegalStateException ("Action needs to be set");

    if (StringHelper.hasNoText (m_sServiceType))
      throw new IllegalStateException ("ServiceType needs to be set");

    if (StringHelper.hasNoText (m_sServiceValue))
      throw new IllegalStateException ("ServiceValue needs to be set");

    if (StringHelper.hasNoText (m_sConversationID))
      throw new IllegalStateException ("ConversationID needs to be set");

    if (StringHelper.hasNoText (m_sAgreementRefPMode))
      throw new IllegalStateException ("AgreementRefPMode needs to be set");

    if (StringHelper.hasNoText (m_sAgreementRefValue))
      throw new IllegalStateException ("AgreementRefValue needs to be set");

    if (StringHelper.hasNoText (m_sFromRole))
      throw new IllegalStateException ("FromRole needs to be set");

    if (StringHelper.hasNoText (m_sFromPartyID))
      throw new IllegalStateException ("FromPartyID needs to be set");

    if (StringHelper.hasNoText (m_sToRole))
      throw new IllegalStateException ("ToRole needs to be set");

    if (StringHelper.hasNoText (m_sToPartyID))
      throw new IllegalStateException ("ToPartyID needs to be set");

    if (m_aEbms3Properties.isEmpty ())
      throw new IllegalStateException ("finalRecipient and originalSender are mandatory properties");
  }

  /**
   * Build the AS4 message to be sent. It uses all the attributes of this class
   * to build the final message. Compression, signing and encryption happens in
   * this methods.
   *
   * @return The HTTP entity to be sent. Never <code>null</code>.
   * @throws Exception
   *         in case something goes wrong
   */
  @Override
  @Nonnull
  public HttpEntity buildMessage () throws Exception
  {
    _checkMandatoryAttributes ();

    final boolean bSign = getCryptoAlgorithmSign () != null && getCryptoAlgorithmSignDigest () != null;
    final boolean bEncrypt = getCryptoAlgorithmCrypt () != null;
    final boolean bAttachmentsPresent = m_aAttachments.isNotEmpty ();

    // Create a new message ID for each build!
    final String sMessageID = StringHelper.getConcatenatedOnDemand (getMessageIDPrefix (),
                                                                    '@',
                                                                    MessageHelperMethods.createRandomMessageID ());

    final Ebms3MessageInfo aEbms3MessageInfo = MessageHelperMethods.createEbms3MessageInfo (sMessageID, null);
    final Ebms3PayloadInfo aEbms3PayloadInfo = CreateUserMessage.createEbms3PayloadInfo (m_aPayload, m_aAttachments);
    final Ebms3CollaborationInfo aEbms3CollaborationInfo = CreateUserMessage.createEbms3CollaborationInfo (m_sAction,
                                                                                                           m_sServiceType,
                                                                                                           m_sServiceValue,
                                                                                                           m_sConversationID,
                                                                                                           m_sAgreementRefPMode,
                                                                                                           m_sAgreementRefValue);
    final Ebms3PartyInfo aEbms3PartyInfo = CreateUserMessage.createEbms3PartyInfo (m_sFromRole,
                                                                                   m_sFromPartyID,
                                                                                   m_sToRole,
                                                                                   m_sToPartyID);

    final Ebms3MessageProperties aEbms3MessageProperties = CreateUserMessage.createEbms3MessageProperties (m_aEbms3Properties);

    final AS4UserMessage aUserMsg = CreateUserMessage.createUserMessage (aEbms3MessageInfo,
                                                                         aEbms3PayloadInfo,
                                                                         aEbms3CollaborationInfo,
                                                                         aEbms3PartyInfo,
                                                                         aEbms3MessageProperties,
                                                                         getSOAPVersion ())
                                                     .setMustUnderstand (true);
    Document aDoc = aUserMsg.getAsSOAPDocument (m_aPayload);

    // 1. compress
    // Is done when the attachments are added

    // 2. sign and/or encrpyt
    MimeMessage aMimeMsg = null;
    if (bSign || bEncrypt)
    {
      _checkKeystoreAttributes ();

      final ICommonsMap <String, String> aCryptoProps = new CommonsLinkedHashMap <> ();
      aCryptoProps.put ("org.apache.wss4j.crypto.provider", "org.apache.wss4j.common.crypto.Merlin");
      aCryptoProps.put ("org.apache.wss4j.crypto.merlin.keystore.file", getKeyStoreFile ().getPath ());
      aCryptoProps.put ("org.apache.wss4j.crypto.merlin.keystore.type", getKeyStoreType ());
      aCryptoProps.put ("org.apache.wss4j.crypto.merlin.keystore.password", getKeyStorePassword ());
      aCryptoProps.put ("org.apache.wss4j.crypto.merlin.keystore.alias", getKeyStoreAlias ());
      final AS4CryptoFactory aCryptoFactory = new AS4CryptoFactory (aCryptoProps);

      // 2a. sign
      if (bSign)
      {
        final boolean bMustUnderstand = true;
        final Document aSignedDoc = new SignedMessageCreator (aCryptoFactory).createSignedMessage (aDoc,
                                                                                                   getSOAPVersion (),
                                                                                                   m_aAttachments,
                                                                                                   m_aResMgr,
                                                                                                   bMustUnderstand,
                                                                                                   getCryptoAlgorithmSign (),
                                                                                                   getCryptoAlgorithmSignDigest ());
        aDoc = aSignedDoc;
      }

      // 2b. encrypt
      if (bEncrypt)
      {
        _checkKeystoreAttributes ();
        final EncryptionCreator aEncCreator = new EncryptionCreator (aCryptoFactory);
        // MustUnderstand always set to true
        final boolean bMustUnderstand = true;
        if (bAttachmentsPresent)
        {
          aMimeMsg = aEncCreator.encryptMimeMessage (getSOAPVersion (),
                                                     aDoc,
                                                     bMustUnderstand,
                                                     m_aAttachments,
                                                     m_aResMgr,
                                                     getCryptoAlgorithmCrypt ());
        }
        else
        {
          aDoc = aEncCreator.encryptSoapBodyPayload (getSOAPVersion (),
                                                     aDoc,
                                                     bMustUnderstand,
                                                     getCryptoAlgorithmCrypt ());
        }
      }
    }

    if (bAttachmentsPresent && aMimeMsg == null)
    {
      // * not encrypted, not signed
      // * not encrypted, signed
      aMimeMsg = new MimeMessageCreator (getSOAPVersion ()).generateMimeMessage (aDoc, m_aAttachments);
    }

    if (aMimeMsg != null)
    {
      return new HttpMimeMessageEntity (aMimeMsg);
    }

    // Wrap SOAP XML
    return new StringEntity (AS4XMLHelper.serializeXML (aDoc));
  }

  public Node getPayload ()
  {
    return m_aPayload;
  }

  /**
   * Sets the payload for a usermessage. The payload unlike an attachment will
   * be added into the SOAP-Body of the message.
   *
   * @param aPayload
   *        the Payload to be added
   */
  public void setPayload (final Node aPayload)
  {
    m_aPayload = aPayload;
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <WSS4JAttachment> getAllAttachments ()
  {
    return m_aAttachments.getClone ();
  }

  /**
   * Adds a file as attachment to the message.
   *
   * @param aAttachment
   *        Attachment to be added. May not be <code>null</code>.
   * @param aMimeType
   *        MIME type of the given file. May not be <code>null</code>.
   * @return this for chaining
   * @throws IOException
   *         if something goes wrong in the adding process
   */
  @Nonnull
  public AS4Client addAttachment (@Nonnull final File aAttachment,
                                  @Nonnull final IMimeType aMimeType) throws IOException
  {
    return addAttachment (aAttachment, aMimeType, null);
  }

  /**
   * Adds a file as attachment to the message.
   *
   * @param aAttachment
   *        Attachment to be added. May not be <code>null</code>.
   * @param aMimeType
   *        MIME type of the given file. May not be <code>null</code>.
   * @param eAS4CompressionMode
   *        which compression type should be used to compress the attachment.
   *        May be <code>null</code>.
   * @return this for chaining
   * @throws IOException
   *         if something goes wrong in the adding process or the compression
   */
  @Nonnull
  public AS4Client addAttachment (@Nonnull final File aAttachment,
                                  @Nonnull final IMimeType aMimeType,
                                  @Nullable final EAS4CompressionMode eAS4CompressionMode) throws IOException
  {
    return addAttachment (WSS4JAttachment.createOutgoingFileAttachment (aAttachment,
                                                                        aMimeType,
                                                                        eAS4CompressionMode,
                                                                        m_aResMgr));
  }

  /**
   * Adds a file as attachment to the message. The caller of the method must
   * ensure the attachment is already compressed (if desired)!
   *
   * @param aAttachment
   *        Attachment to be added. May not be <code>null</code>.
   * @return this for chaining
   */
  @Nonnull
  public AS4Client addAttachment (@Nonnull final WSS4JAttachment aAttachment)
  {
    ValueEnforcer.notNull (aAttachment, "Attachment");
    m_aAttachments.add (aAttachment);
    return this;
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <Ebms3Property> getAllEbms3Properties ()
  {
    return m_aEbms3Properties.getClone ();
  }

  /**
   * With properties optional info can be added for the receiving party. If you
   * want to be AS4 Profile conform you need to add two properties to your
   * message: originalSender and finalRecipient these two correlate to C1 and
   * C4.
   *
   * @param aEbms3Properties
   *        Properties that should be set in the current usermessage
   */
  public void setEbms3Properties (@Nullable final ICommonsList <Ebms3Property> aEbms3Properties)
  {
    m_aEbms3Properties.setAll (aEbms3Properties);
  }

  public String getAction ()
  {
    return m_sAction;
  }

  /**
   * The element is a string identifying an operation or an activity within a
   * Service that may support several of these.<br>
   * Example of what will be written in the usermessage:
   * <code>&lt;eb:Action&gt;NewPurchaseOrder&lt;/eb:Action&gt;</code><br>
   * This is MANDATORY.
   *
   * @param sAction
   *        the action that should be there.
   */
  public void setAction (final String sAction)
  {
    m_sAction = sAction;
  }

  public String getServiceType ()
  {
    return m_sServiceType;
  }

  /**
   * It is a string identifying the servicetype of the service specified in
   * servicevalue.<br>
   * Example of what will be written in the usermessage:
   * <code>&lt;eb:Service type= "MyServiceTypes"&gt;QuoteToCollect&lt;/eb:Service&gt;</code><br>
   *
   * @param sServiceType
   *        serviceType that should be set
   */
  public void setServiceType (final String sServiceType)
  {
    m_sServiceType = sServiceType;
  }

  public String getServiceValue ()
  {
    return m_sServiceValue;
  }

  /**
   * It is a string identifying the service that acts on the message 1639 and it
   * is specified by the designer of the service.<br>
   * Example of what will be written in the usermessage: <code>&lt;eb:Service
   * type="MyServiceTypes"&gt;QuoteToCollect&lt;/eb:Service&gt;</code><br>
   * This is MANDATORY.
   *
   * @param sServiceValue
   *        the servicevalue that should be set
   */
  public void setServiceValue (final String sServiceValue)
  {
    m_sServiceValue = sServiceValue;
  }

  public String getConversationID ()
  {
    return m_sConversationID;
  }

  /**
   * The element is a string identifying the set of related messages that make
   * up a conversation between Parties.<br>
   * Example of what will be written in the usermessage:
   * <code>&lt;eb:ConversationId&gt;4321&lt;/eb:ConversationId&gt;</code><br>
   * This is MANDATORY.
   *
   * @param sConversationID
   *        the conversationID that should be set
   */
  public void setConversationID (final String sConversationID)
  {
    m_sConversationID = sConversationID;
  }

  public String getAgreementRefPMode ()
  {
    return m_sAgreementRefPMode;
  }

  /**
   * The AgreementRef element requires a PModeID which can be set with this
   * method.<br>
   * Example of what will be written in the usermessage:
   * <code>&lt;eb:AgreementRef pmode=
   * "pm-esens-generic-resp"&gt;http://agreements.holodeckb2b.org/examples/agreement0&lt;/eb:AgreementRef&gt;</code><br>
   * This is MANDATORY.
   *
   * @param sAgreementRefPMode
   *        PMode that should be used (id)
   */
  public void setAgreementRefPMode (final String sAgreementRefPMode)
  {
    m_sAgreementRefPMode = sAgreementRefPMode;
  }

  public String getAgreementRefValue ()
  {
    return m_sAgreementRefValue;
  }

  /**
   * The AgreementRef element is a string that identifies the entity or artifact
   * governing the exchange of messages between the parties.<br>
   * Example of what will be written in the usermessage:
   * <code>&lt;eb:AgreementRef pmode=
   * "pm-esens-generic-resp"&gt;http://agreements.holodeckb2b.org/examples/agreement0&lt;/eb:AgreementRef&gt;</code><br>
   * This is MANDATORY.
   *
   * @param sAgreementRefValue
   *        agreementreference that should be set
   */
  public void setAgreementRefValue (final String sAgreementRefValue)
  {
    m_sAgreementRefValue = sAgreementRefValue;
  }

  public String getFromRole ()
  {
    return m_sFromRole;
  }

  /**
   * The value of the Role element is a non-empty string, with a default value
   * of
   * <code>http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultRole</code>
   * .
   *
   * @param sFromRole
   *        the role that should be set
   */
  public void setFromRole (final String sFromRole)
  {
    m_sFromRole = sFromRole;
  }

  public String getFromPartyID ()
  {
    return m_sFromPartyID;
  }

  /**
   * The PartyID is an ID that identifies the C2 over which the message gets
   * sent.<br>
   * Example of what will be written in the usermessage:
   * <code>&lt;eb:PartyId&gt;ImAPartyID&lt;/eb:PartyId&gt;</code><br>
   * This is MANDATORY.
   *
   * @param sFromPartyID
   *        the partyID that should be set
   */
  public void setFromPartyID (final String sFromPartyID)
  {
    m_sFromPartyID = sFromPartyID;
  }

  public String getToRole ()
  {
    return m_sToRole;
  }

  /**
   * @see #setFromRole(String)
   * @param sToRole
   *        the role that should be used
   */
  public void setToRole (final String sToRole)
  {
    m_sToRole = sToRole;
  }

  public String getToPartyID ()
  {
    return m_sToPartyID;
  }

  /**
   * * @see #setFromPartyID(String)
   *
   * @param sToPartyID
   *        the PartyID that should be set
   */
  public void setToPartyID (final String sToPartyID)
  {
    m_sToPartyID = sToPartyID;
  }
}
