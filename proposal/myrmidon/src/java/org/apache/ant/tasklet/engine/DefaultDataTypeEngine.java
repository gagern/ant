/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.ant.tasklet.engine;

import org.apache.ant.tasklet.DataType;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.camelot.DefaultRegistry;
import org.apache.avalon.framework.camelot.Factory;
import org.apache.avalon.framework.camelot.FactoryException;
import org.apache.avalon.framework.camelot.Locator;
import org.apache.avalon.framework.camelot.Registry;
import org.apache.avalon.framework.camelot.RegistryException;

/**
 * This is basically a engine that can be used to access data-types.
 * The engine acts as a repository and factory for these types.
 *
 * @author <a href="mailto:donaldp@apache.org">Peter Donald</a>
 */
public class DefaultDataTypeEngine
    implements DataTypeEngine, Composable
{
    protected Factory              m_factory;
    protected Registry             m_registry  = new DefaultRegistry( Locator.class );
    
    /**
     * Retrieve registry of data-types.
     * This is used by deployer to add types into engine.
     *
     * @return the registry
     */
    public Registry getRegistry()
    {
        return m_registry;
    }
    
    /**
     * Retrieve relevent services needed to deploy.
     *
     * @param componentManager the ComponentManager
     * @exception ComponentException if an error occurs
     */
    public void compose( final ComponentManager componentManager )
        throws ComponentException
    {
        m_factory = (Factory)componentManager.lookup( "org.apache.avalon.framework.camelot.Factory" );
    }
    
    /**
     * Create a data-type of type registered under name.
     *
     * @param name the name of data type
     * @return the DataType
     * @exception RegistryException if an error occurs
     * @exception FactoryException if an error occurs
     */    
    public DataType createDataType( final String name )
        throws RegistryException, FactoryException
    {
        final Locator locator = (Locator)m_registry.getInfo( name, Locator.class );
        return (DataType)m_factory.create( locator, DataType.class );
    }
}
