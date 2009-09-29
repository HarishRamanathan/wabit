/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

import ca.sqlpower.query.Item;

/**
 * This type of {@link Item} wraps any other type of item to let other classes
 * attach {@link WabitListener}s to the delegate.
 */
public abstract class WabitItem extends AbstractWabitObject {
	
    /**
     * The delegate to send the method calls to.
     */
	private final Item delegate;
	
	/**
	 * A change listener on the delegate that will change the source of the
	 * events to this object and refire them.
	 */
	private final PropertyChangeListener delegateChangeListener = new PropertyChangeListener() {
    
        public void propertyChange(PropertyChangeEvent evt) {
        	if (evt.getPropertyName().equals("name")) {
        		setName((String) evt.getNewValue());
        	}
            firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    };
    
	public WabitItem(Item delegate) {
		super();
		this.delegate = delegate;
		delegate.addPropertyChangeListener(delegateChangeListener);
		setName(delegate.getName());
	}
	
	@Override
	public CleanupExceptions cleanup() {
		delegate.removePropertyChangeListener(delegateChangeListener);
		return new CleanupExceptions();
	}
	
	public Item getDelegate() {
	    return delegate;
	}

	@Override
	protected boolean removeChildImpl(WabitObject child) {
		return false;
	}

	public boolean allowsChildren() {
		return false;
	}

	public int childPositionOffset(Class<? extends WabitObject> childType) {
		return 0;
	}

	public List<? extends WabitObject> getChildren() {
		return Collections.emptyList();
	}

	public List<WabitObject> getDependencies() {
		return Collections.emptyList();
	}

	public void removeDependency(WabitObject dependency) {
		// do nothing
	}

}