package com.helger.as4lib.model.pmode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.ext.ICommonsList;
import com.helger.commons.state.EChange;
import com.helger.photon.basic.app.dao.impl.AbstractMapBasedWALDAO;
import com.helger.photon.basic.app.dao.impl.DAOException;
import com.helger.photon.basic.audit.AuditHelper;
import com.helger.photon.security.object.ObjectHelper;

public class PModeManager extends AbstractMapBasedWALDAO <IPMode, PMode>
{
  public PModeManager (@Nullable final String sFilename) throws DAOException
  {
    super (PMode.class, sFilename);
  }

  @Nonnull
  public IPMode createPMode (@Nonnull final PMode aPMode)
  {
    ValueEnforcer.notNull (aPMode, "PMode");

    m_aRWLock.writeLocked ( () -> {
      internalCreateItem (aPMode);
    });
    AuditHelper.onAuditCreateSuccess (PMode.OT, aPMode.getID ());

    return aPMode;
  }

  @Nonnull
  public EChange updatePMode (@Nonnull final IPMode aPMode)
  {
    ValueEnforcer.notNull (aPMode, "PMode");
    final PMode aRealPMode = getOfID (aPMode.getID ());
    if (aRealPMode == null)
    {
      AuditHelper.onAuditModifyFailure (PMode.OT, aPMode.getID (), "no-such-id");
      return EChange.UNCHANGED;
    }

    m_aRWLock.writeLock ().lock ();
    try
    {
      ObjectHelper.setLastModificationNow (aRealPMode);
      internalUpdateItem (aRealPMode);
    }
    finally
    {
      m_aRWLock.writeLock ().unlock ();
    }
    AuditHelper.onAuditModifySuccess (PMode.OT, "all", aRealPMode.getID ());

    return EChange.CHANGED;
  }

  @Nonnull
  public EChange deletePMode (@Nullable final String sPModeID)
  {
    final PMode aDeletedPMode = getOfID (sPModeID);
    if (aDeletedPMode == null)
    {
      AuditHelper.onAuditDeleteFailure (PMode.OT, "no-such-object-id", sPModeID);
      return EChange.UNCHANGED;
    }

    m_aRWLock.writeLock ().lock ();
    try
    {
      if (ObjectHelper.setDeletionNow (aDeletedPMode).isUnchanged ())
      {
        AuditHelper.onAuditDeleteFailure (PMode.OT, "already-deleted", sPModeID);
        return EChange.UNCHANGED;
      }
      internalMarkItemDeleted (aDeletedPMode);
    }
    finally
    {
      m_aRWLock.writeLock ().unlock ();
    }
    AuditHelper.onAuditDeleteSuccess (PMode.OT, sPModeID);

    return EChange.CHANGED;
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <IPMode> getAllPModes ()
  {
    return getAll ();
  }

  @Nullable
  public IPMode getPModeOfID (@Nullable final String sID)
  {
    return getOfID (sID);
  }
}