create or replace trigger %%LIBRARY%%.%%TRIGGER_NAME%%
    after update or insert or delete on %%SOURCE_SCHEMA%%.%%SOURCE_TABLE%%
    referencing new as n old as o for each row
  when (inserting or updating or deleting)
  begin atomic
    declare operation varchar(10) for sbcs data;
    if inserting then
      set operation = 'INSERT';
    end if;
    if deleting then
      set operation = 'DELETE';
    end if;
    if updating then
      set operation = 'UPDATE';
    end if;
    if (inserting or updating) then
    set %%LIBRARY%%.%%DATA_QUEUE_NAME%% = JSON_OBJECT(KEY 'table' VALUE '%%SOURCE_TABLE%%', KEY 'operation' VALUE operation, 
                                      KEY 'row' VALUE 
                                      JSON_OBJECT(
                                        %%COLUMN_DATA%%
                                      ));
    else 
    set %%LIBRARY%%.%%DATA_QUEUE_NAME%% = JSON_OBJECT(KEY 'table' VALUE '%%SOURCE_TABLE%%', KEY 'operation' VALUE operation, 
                                      KEY 'row' VALUE 
                                      JSON_OBJECT(
                                        %%COLUMN_DATA_ON_DELETE%%
                                      ));    end if;
    call qsys2.send_data_queue_utf8(
        message_data       => %%LIBRARY%%.%%DATA_QUEUE_NAME%%, 
        data_queue         => '%%DATA_QUEUE_NAME%%',
        data_queue_library => '%%LIBRARY%%');
  end