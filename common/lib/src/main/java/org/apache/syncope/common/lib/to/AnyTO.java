/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.common.lib.to;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

@XmlType
@XmlSeeAlso({ UserTO.class, GroupTO.class, AnyObjectTO.class })
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public abstract class AnyTO extends AbstractAnnotatedBean implements EntityTO, AttributableTO {

    private static final long serialVersionUID = -754311920679872084L;

    private String key;

    private String type;

    private String realm;

    private final List<String> dynRealms = new ArrayList<>();

    private String status;

    private final List<String> auxClasses = new ArrayList<>();

    private final Set<AttrTO> plainAttrs = new HashSet<>();

    private final Set<AttrTO> derAttrs = new HashSet<>();

    private final Set<AttrTO> virAttrs = new HashSet<>();

    private final Set<String> resources = new HashSet<>();

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(final String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(final String realm) {
        this.realm = realm;
    }

    @XmlElementWrapper(name = "dynRealms")
    @XmlElement(name = "dynRealmF")
    @JsonProperty("dynRealms")
    public List<String> getDynRealms() {
        return dynRealms;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    @XmlElementWrapper(name = "auxClasses")
    @XmlElement(name = "class")
    @JsonProperty("auxClasses")
    public List<String> getAuxClasses() {
        return auxClasses;
    }

    @XmlElementWrapper(name = "plainAttrs")
    @XmlElement(name = "attribute")
    @JsonProperty("plainAttrs")
    @Override
    public Set<AttrTO> getPlainAttrs() {
        return plainAttrs;
    }

    @JsonIgnore
    @Override
    public Optional<AttrTO> getPlainAttr(final String schema) {
        return plainAttrs.stream().filter(attr -> attr.getSchema().equals(schema)).findFirst();
    }

    @XmlElementWrapper(name = "derAttrs")
    @XmlElement(name = "attribute")
    @JsonProperty("derAttrs")
    @Override
    public Set<AttrTO> getDerAttrs() {
        return derAttrs;
    }

    @JsonIgnore
    @Override
    public Optional<AttrTO> getDerAttr(final String schema) {
        return derAttrs.stream().filter(attr -> attr.getSchema().equals(schema)).findFirst();
    }

    @XmlElementWrapper(name = "virAttrs")
    @XmlElement(name = "attribute")
    @JsonProperty("virAttrs")
    @Override
    public Set<AttrTO> getVirAttrs() {
        return virAttrs;
    }

    @JsonIgnore
    @Override
    public Optional<AttrTO> getVirAttr(final String schema) {
        return virAttrs.stream().filter(attr -> attr.getSchema().equals(schema)).findFirst();
    }

    @XmlElementWrapper(name = "resources")
    @XmlElement(name = "resource")
    @JsonProperty("resources")
    public Set<String> getResources() {
        return resources;
    }

}
