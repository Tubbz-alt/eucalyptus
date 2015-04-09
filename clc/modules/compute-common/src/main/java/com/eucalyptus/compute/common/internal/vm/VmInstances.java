/*************************************************************************
 * Copyright 2009-2015 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/
package com.eucalyptus.compute.common.internal.vm;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityTransaction;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import com.eucalyptus.compute.common.CloudMetadata;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.records.Logs;
import com.eucalyptus.util.CollectionUtils;
import com.eucalyptus.util.OwnerFullName;
import com.eucalyptus.util.RestrictedTypes;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 *
 */
public class VmInstances {

  private static final Logger LOG = Logger.getLogger( VmInstances.class );


  protected static final AtomicReference<String> ebsRootDeviceName = new AtomicReference<String>( "emi" );

  public static String getEbsRootDeviceName( ) {
    return ebsRootDeviceName.get( );
  }

  /**
   * Lookup a VM instance.
   *
   * @param name The instance identifier (display name)
   * @return The instance.
   * @throws java.util.NoSuchElementException If the instance is not found
   */
  @Nonnull
  public static VmInstance lookupAny( final String name ) throws NoSuchElementException {
    return PersistentLookup.INSTANCE.apply( name );
  }

  /**
   * Lookup a non-terminated VM instance.
   *
   * @param name The instance identifier (display name)
   * @return The instance.
   * @throws NoSuchElementException If the instance is not found
   * @throws TerminatedInstanceException If the instance is terminated.
   */
  @Nonnull
  public static VmInstance lookup( final String name ) throws NoSuchElementException, TerminatedInstanceException {
    return lookup( ).apply( name );
  }

  /**
   * Function for lookup of non-terminated VM instances.
   *
   * <p>The function parameter is the instance identifier the return is the
   * instance. The function will not return null, but may throw
   * NoSuchElementException or TerminatedInstanceException.</p>
   *
   * @return The function.
   */
  public static Function<String,VmInstance> lookup() {
    return Functions.compose( TerminatedInstanceCheck.INSTANCE, PersistentLookup.INSTANCE );
  }

  /**
   * List instances that are not done and match the given predicate.
   *
   * @param predicate The predicate to match
   * @return The matching instances
   * @see com.eucalyptus.compute.common.internal.vm.VmInstance.VmStateSet#DONE
   */
  public static List<VmInstance> list( @Nullable Predicate<? super VmInstance> predicate ) {
    return list( (OwnerFullName) null, predicate );
  }

  /**
   * List instances that are not done and match the given owner/predicate.
   *
   * @param ownerFullName The owning user or account
   * @param predicate The predicate to match
   * @return The matching instances
   * @see com.eucalyptus.compute.common.internal.vm.VmInstance.VmStateSet#DONE
   */
  public static List<VmInstance> list( @Nullable OwnerFullName ownerFullName,
                                       @Nullable Predicate<? super VmInstance> predicate ) {
    return list(
        ownerFullName,
        Restrictions.not( VmInstance.criterion( VmInstance.VmStateSet.DONE.array() ) ),
        Collections.<String,String>emptyMap(),
        Predicates.and( VmInstance.VmStateSet.DONE.not(), predicate ) );
  }

  /**
   * List instances in any state that match the given parameters.
   */
  public static List<VmInstance> list( @Nullable final OwnerFullName ownerFullName,
                                       final Criterion criterion,
                                       final Map<String,String> aliases,
                                       @Nullable final Predicate<? super VmInstance> predicate ) {
    return list( new Supplier<List<VmInstance>>() {
      @Override
      public List<VmInstance> get() {
        return Entities.query( VmInstance.named( ownerFullName, null ), false, criterion, aliases );
      }
    }, Predicates.and(
        RestrictedTypes.filterByOwner( ownerFullName ),
        checkPredicate( predicate )
    ) );
  }

  /**
   * List instances in any state that match the given parameters.
   */
  public static List<VmInstance> listByClientToken( @Nullable final OwnerFullName ownerFullName,
                                                    @Nullable final String clientToken,
                                                    @Nullable Predicate<? super VmInstance> predicate ) {
    return list( new Supplier<List<VmInstance>>() {
      @Override
      public List<VmInstance> get() {
        return Entities.query( VmInstance.withToken( ownerFullName, clientToken ) );
      }
    }, Predicates.and(
        CollectionUtils.propertyPredicate( clientToken, VmInstance.clientToken( ) ),
        RestrictedTypes.filterByOwner( ownerFullName ),
        checkPredicate( predicate )
    ) );
  }

  private static List<VmInstance> list( @Nonnull Supplier<List<VmInstance>> instancesSupplier,
                                        @Nullable Predicate<? super VmInstance> predicate ) {
    predicate = checkPredicate( predicate );
    return listPersistent( instancesSupplier, predicate );
  }

  private static List<VmInstance> listPersistent( @Nonnull Supplier<List<VmInstance>> instancesSupplier,
                                                  @Nonnull Predicate<? super VmInstance> predicate ) {
    final EntityTransaction db = Entities.get( VmInstance.class );
    try {
      final Iterable<VmInstance> vms = Iterables.filter( instancesSupplier.get(), predicate );
      final List<VmInstance> instances = Lists.newArrayList( vms );
      db.commit( );
      return instances;
    } catch ( final Exception ex ) {
      LOG.error( ex );
      Logs.extreme().error( ex, ex );
      return Lists.newArrayList( );
    } finally {
      if ( db.isActive() ) db.rollback();
    }
  }

  private static <T> Predicate<T> checkPredicate( Predicate<T> predicate ) {
    return predicate == null ?
        Predicates.<T>alwaysTrue() :
        predicate;
  }

  public static VmVolumeAttachment lookupVolumeAttachment( final String volumeId , final List<VmInstance> vms ) {
    VmVolumeAttachment ret = null;
    try {
      for ( VmInstance vm : vms ) {
        try {
          ret = vm.lookupVolumeAttachment( volumeId );
          if ( ret.getVmInstance( ) == null ) {
            ret.setVmInstance( vm );
          }
        } catch ( NoSuchElementException ex ) {
          continue;
        }
      }
      if ( ret == null ) {
        throw new NoSuchElementException( "VmVolumeAttachment: no volume attachment for " + volumeId );
      }
      return ret;
    } catch ( Exception ex ) {
      throw new NoSuchElementException( ex.getMessage( ) );
    }
  }

  @RestrictedTypes.Resolver( CloudMetadata.VmInstanceMetadata.class )
  enum PersistentLookup implements Function<String, VmInstance> {
    INSTANCE;

    /**
     * @see com.google.common.base.Function#apply(java.lang.Object)
     */
    @Nonnull
    @Override
    public VmInstance apply( final String name ) {
      return VmInstance.Lookup.INSTANCE.apply( name );
    }
  }

  enum TerminatedInstanceCheck implements Function<VmInstance,VmInstance> {
    INSTANCE;

    @Nullable
    @Override
    public VmInstance apply( final VmInstance instance ) {
      if ( instance != null && VmInstance.VmStateSet.DONE.apply( instance ) ) {
        throw new TerminatedInstanceException( instance.getDisplayName( ) );
      }
      return instance;
    }
  }

  public static class TerminatedInstanceException extends NoSuchElementException {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    TerminatedInstanceException( final String s ) {
      super( s );
    }

  }
}
