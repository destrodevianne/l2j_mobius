/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jmobius.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.l2jmobius.commons.mmocore.IAcceptFilter;

/**
 * IPv4 filter.
 * @author Forsaiken, Zoey76
 */
public class IPv4Filter implements IAcceptFilter, Runnable
{
	private static final Logger LOG = Logger.getLogger(IPv4Filter.class.getName());
	private final HashMap<Integer, Flood> _ipFloodMap;
	private static final long SLEEP_TIME = 5000;
	
	public IPv4Filter()
	{
		_ipFloodMap = new HashMap<>();
		final Thread t = new Thread(this, getClass().getSimpleName());
		t.setDaemon(true);
		t.start();
	}
	
	/**
	 * @param ip
	 * @return
	 */
	private static final int hash(byte[] ip)
	{
		return (ip[0] & 0xFF) | ((ip[1] << 8) & 0xFF00) | ((ip[2] << 16) & 0xFF0000) | ((ip[3] << 24) & 0xFF000000);
	}
	
	protected static final class Flood
	{
		long lastAccess;
		int trys;
		
		Flood()
		{
			lastAccess = System.currentTimeMillis();
			trys = 0;
		}
	}
	
	@Override
	public boolean accept(SocketChannel sc)
	{
		final InetAddress addr = sc.socket().getInetAddress();
		if (!(addr instanceof Inet4Address))
		{
			LOG.info(IPv4Filter.class.getSimpleName() + ": Someone tried to connect from something other than IPv4: " + addr.getHostAddress());
			return false;
		}
		
		final int h = hash(addr.getAddress());
		final long current = System.currentTimeMillis();
		Flood f;
		synchronized (_ipFloodMap)
		{
			f = _ipFloodMap.get(h);
		}
		if (f != null)
		{
			if (f.trys == -1)
			{
				f.lastAccess = current;
				return false;
			}
			
			f.lastAccess = current;
			if ((f.lastAccess + 1000) > current)
			{
				if (f.trys >= 3)
				{
					f.trys = -1;
					return false;
				}
				f.trys++;
			}
		}
		else
		{
			synchronized (_ipFloodMap)
			{
				_ipFloodMap.put(h, new Flood());
			}
		}
		
		return true;
	}
	
	@Override
	public void run()
	{
		while (true)
		{
			final long reference = System.currentTimeMillis() - (1000 * 300);
			synchronized (_ipFloodMap)
			{
				final Iterator<Entry<Integer, Flood>> it = _ipFloodMap.entrySet().iterator();
				while (it.hasNext())
				{
					if (it.next().getValue().lastAccess < reference)
					{
						it.remove();
					}
				}
			}
			
			try
			{
				Thread.sleep(SLEEP_TIME);
			}
			catch (InterruptedException e)
			{
				return;
			}
		}
	}
}