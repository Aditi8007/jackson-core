package com.fasterxml.jackson.core.json;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.CharTypes;

/**
 * Extension of {@link JsonStreamContext}, which implements
 * core methods needed, and also exposes
 * more complete API to parser implementation classes.
 */
public final class JsonReadContext
    extends JsonStreamContext
{
    // // // Configuration

    protected final JsonReadContext _parent;
    
    // // // Location information (minus source reference)

    protected int _lineNr;
    protected int _columnNr;

    protected String _currentName;

    // // // Optional duplicate detection

    protected DupDetector _dups;
    
    /*
    /**********************************************************
    /* Simple instance reuse slots; speeds up things
    /* a bit (10-15%) for docs with lots of small
    /* arrays/objects (for which allocation was
    /* visible in profile stack frames)
    /**********************************************************
     */

    protected JsonReadContext _child = null;

    /*
    /**********************************************************
    /* Instance construction, reuse
    /**********************************************************
     */

    public JsonReadContext(JsonReadContext parent, int type, int lineNr, int colNr)
    {
        super();
        _type = type;
        _parent = parent;
        _lineNr = lineNr;
        _columnNr = colNr;
        _index = -1;
    }

    protected void reset(int type, int lineNr, int colNr)
    {
        _type = type;
        _index = -1;
        _lineNr = lineNr;
        _columnNr = colNr;
        _currentName = null;
        if (_dups != null) {
            _dups.reset();
        }
    }

    public void trackDups(JsonParser jp) {
        _dups = DupDetector.rootDetector(jp);
    }

    // // // Factory methods

    public static JsonReadContext createRootContext(int lineNr, int colNr)
    {
        return new JsonReadContext(null, TYPE_ROOT, lineNr, colNr);
    }

    public static JsonReadContext createRootContext()
    {
        return new JsonReadContext(null, TYPE_ROOT, 1, 0);
    }
    
    public JsonReadContext createChildArrayContext(int lineNr, int colNr)
    {
        JsonReadContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new JsonReadContext(this, TYPE_ARRAY, lineNr, colNr);
        } else {
            ctxt.reset(TYPE_ARRAY, lineNr, colNr);
        }
        if (_dups != null) {
            // must pass a placeholder to indicate that tracking is on; not used
            ctxt._dups = _dups;
        }
        return ctxt;
    }

    public JsonReadContext createChildObjectContext(int lineNr, int colNr)
    {
        JsonReadContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new JsonReadContext(this, TYPE_OBJECT, lineNr, colNr);
            if (_dups != null) {
                ctxt._dups = _dups.child();
            }
            return ctxt;
        }
        ctxt.reset(TYPE_OBJECT, lineNr, colNr);
        return ctxt;
    }

    /*
    /**********************************************************
    /* Abstract method implementation
    /**********************************************************
     */

    @Override
    public String getCurrentName() { return _currentName; }

    @Override
    public JsonReadContext getParent() { return _parent; }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    /**
     * @return Location pointing to the point where the context
     *   start marker was found
     */
    public JsonLocation getStartLocation(Object srcRef)
    {
        /* We don't keep track of offsets at this level (only
         * reader does)
         */
        long totalChars = -1L;

        return new JsonLocation(srcRef, totalChars, _lineNr, _columnNr);
    }

    /*
    /**********************************************************
    /* State changes
    /**********************************************************
     */

    public boolean expectComma()
    {
        /* Assumption here is that we will be getting a value (at least
         * before calling this method again), and
         * so will auto-increment index to avoid having to do another call
         */
        int ix = ++_index; // starts from -1
        return (_type != TYPE_ROOT && ix > 0);
    }

    public void setCurrentName(String name) throws JsonProcessingException
    {
        _currentName = name;
        if (_dups != null) {
            _checkDup(_dups, name);
        }
    }

    private void _checkDup(DupDetector dd, String name) throws JsonProcessingException
    {
        if (dd.isDup(name)) {
            throw new JsonParseException("Duplicate field '"+name+"'", dd.findLocation());
        }
    }
    
    /*
    /**********************************************************
    /* Overridden standard methods
    /**********************************************************
     */

    /**
     * Overridden to provide developer readable "JsonPath" representation
     * of the context.
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(64);
        switch (_type) {
        case TYPE_ROOT:
            sb.append("/");
            break;
        case TYPE_ARRAY:
            sb.append('[');
            sb.append(getCurrentIndex());
            sb.append(']');
            break;
        case TYPE_OBJECT:
            sb.append('{');
            if (_currentName != null) {
                sb.append('"');
                CharTypes.appendQuoted(sb, _currentName);
                sb.append('"');
            } else {
                sb.append('?');
            }
            sb.append('}');
            break;
        }
        return sb.toString();
    }
}
