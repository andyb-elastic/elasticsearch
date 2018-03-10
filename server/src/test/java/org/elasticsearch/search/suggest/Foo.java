package org.elasticsearch.search.suggest;

import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Foo {

    public static class Outer<T extends Inner> implements NamedWriteable {

        public T inner;

        public Outer(StreamInput in) throws IOException {
            //this.inner = in.readNamedWriteable(Inner.class);
        }

        @Override
        public String getWriteableName() {
            return "outer";
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeNamedWriteable(inner);
        }
    }

    public static class Inner implements NamedWriteable {

        public int value;

        public Inner(StreamInput in) throws IOException {
            this.value = in.readVInt();
        }

        @Override
        public String getWriteableName() {
            return "inner";
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(value);
        }
    }

    public static class ListUpper {
        public List<Upper> getIt() {
            return new ArrayList<>();
        }
    }
    public static class ListLower extends ListUpper {
        /*@Override
        public List<Lower> getIt() {
            return new ArrayList<Lower>();
        }*/
    }
    public static class Upper {}
    public static class Lower extends Upper {}
}
