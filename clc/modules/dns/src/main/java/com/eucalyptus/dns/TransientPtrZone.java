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

package com.eucalyptus.dns;

import java.io.IOException;
import java.net.InetAddress;
import org.apache.log4j.Logger;
import org.xbill.DNS.DClass;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.PTRRecord;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.SetResponse;

import com.eucalyptus.util.Internets;
import com.eucalyptus.util.dns.DomainNames;
import com.eucalyptus.compute.common.internal.vm.VmInstance;
import com.eucalyptus.vm.VmInstances;
import com.eucalyptus.ws.StackConfiguration;


public class TransientPtrZone extends Zone {
  private static Logger LOG = Logger.getLogger( TransientPtrZone.class );
  private static int ttl               = 604800;
  
  public TransientPtrZone( Name name, Record[] records ) throws IOException {
    super(name,records);
  }

  /* (non-Javadoc)
 * @see com.eucalyptus.dns.Zone#findRecords(org.xbill.DNS.Name, int)
 */
@Override
  public SetResponse findRecords( Name name, int type, InetAddress listenerAddress ) {
    if (StackConfiguration.USE_INSTANCE_DNS && name.toString().endsWith(".in-addr.arpa.")) {
  	  int index = name.toString().indexOf(".in-addr.arpa.");
  	  Name target;
	  if ( index > 0 ) {
		String ipString = name.toString().substring(0, index);
		String[] parts = ipString.split("\\.");
		String ipCandidate;
		if (parts.length == 4) {
	      ipCandidate = new StringBuffer()
	          .append(parts[3]).append(".")
	          .append(parts[2]).append(".")
	          .append(parts[1]).append(".")
	          .append(parts[0]).toString( );		  	
		} else {
		  return super.findRecords( name, type, listenerAddress );
		}
		try {
	      VmInstance instance = VmInstances.lookupByPublicIp( ipCandidate );
	      target = new Name( instance.getPublicDnsName( ) + ".");
	    } catch ( Exception e ) {
	      if ( VmInstances.privateIpInUse( ipCandidate ) ) try {
	        target = new Name( VmInstances.generateDnsName( ipCandidate, DomainNames.internalSubdomain( ) ) + "." );
	      } catch ( Exception e1 ) {
          return super.findRecords( name, type, listenerAddress );
        } else {
	        return super.findRecords( name, type, listenerAddress );
	      }
	    }
        SetResponse resp = new SetResponse(SetResponse.SUCCESSFUL);
        resp.addRRset( new RRset( new PTRRecord( name, DClass.IN, ttl, target ) ) );
        return resp;
	  } else {
	    return super.findRecords( name, type, listenerAddress );
	  }
	} else {
      return super.findRecords( name, type, listenerAddress );
    }
  }

  public static Zone getPtrZone(Name queryName) {
    try {
      String nameString = queryName.toString();
      Name name;
	  int index = nameString.indexOf(".in-addr.arpa.");
	  if ( index > 0 ) {
		 String ipString = nameString.substring(0, index);
		 String[] parts = ipString.split("\\.");
		 //fix this for v6
		 if(parts.length == 4) {
		   nameString = nameString.substring(parts[0].length() + 1);
		   name = new Name(nameString);    	 
		 } else {
		   return null;
		 }
	  } else {
	    return null;
	  }
	  long serial = 1;
	  long refresh = 86400;
	  long retry = ttl;
	  long expires = 2419200;
          //This is the negative cache TTL
          long minimum = 600;
	  Record soarec = new SOARecord( name, DClass.IN, ttl, name, Name.fromString( "root." + name.toString( ) ), serial,
	    refresh, retry, expires, minimum );
	  long nsTTL = 604800;
	  Record nsrec = new NSRecord( name, DClass.IN, nsTTL,
	    Name.fromString( Internets.localHostInetAddress( ).getCanonicalHostName( ) +".") );
	  return new TransientPtrZone( name, new Record[] { soarec, nsrec } );
	} catch ( Exception e ) {
	  LOG.error( e, e );
	  return null;
	} 	
  }

  
}
