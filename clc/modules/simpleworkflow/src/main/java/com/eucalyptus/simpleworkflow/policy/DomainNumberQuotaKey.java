/*************************************************************************
 * Copyright 2009-2014 Eucalyptus Systems, Inc.
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
 ************************************************************************/
package com.eucalyptus.simpleworkflow.policy;

import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.policy.PolicySpec;
import com.eucalyptus.auth.policy.key.KeyUtils;
import com.eucalyptus.auth.policy.key.PolicyKey;
import com.eucalyptus.auth.policy.key.QuotaKey;
import com.eucalyptus.auth.principal.AccountFullName;
import com.eucalyptus.auth.principal.UserFullName;
import com.eucalyptus.simpleworkflow.common.SimpleWorkflowMetadata;
import com.eucalyptus.util.RestrictedTypes;
import net.sf.json.JSONException;

/**
 *
 */

@PolicyKey( DomainNumberQuotaKey.KEY )
public class DomainNumberQuotaKey extends QuotaKey {

  public static final String KEY = "swf:quota-domainnumber";

  @Override
  public void validateValueType( String value ) throws JSONException {
    KeyUtils.validateIntegerValue( value, KEY );
  }

  @Override
  public boolean canApply( String action, String resourceType ) {
    return PolicySpec.qualifiedName( "swf", "registerdomain" ).equals( action );
  }

  @Override
  public String value( Scope scope, String id, String resource, Long quantity ) throws AuthException {
    switch ( scope ) {
      case ACCOUNT:
        return Long.toString( RestrictedTypes.quantityMetricFunction( SimpleWorkflowMetadata.DomainMetadata.class ).apply( AccountFullName.getInstance( id ) ) + quantity );
      case GROUP:
        return NOT_SUPPORTED;
      case USER:
        return Long.toString( RestrictedTypes.quantityMetricFunction( SimpleWorkflowMetadata.DomainMetadata.class ).apply( UserFullName.getInstance( id ) ) + quantity );
    }
    throw new AuthException( "Invalid scope" );
  }
}
