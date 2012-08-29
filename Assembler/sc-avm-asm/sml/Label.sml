structure Label : LABEL =
struct

open Error

datatype t =
	 (* A global label, such as FOO *)
	 Global of string
       (* A local label, such as .12 *)
       | Local of int

fun parseDigits substr = 
    case Numbers.parseDigits substr StringCvt.DEC of 
	SOME (n,substr') => 
	((Numbers.normalize 100 n,substr') 
	 handle RangeError => raise BadLabel)
      | _   => raise BadLabel
		     
fun isLabelChar c =
    Char.isAlphaNum c orelse c = #"."
			     
fun parse substr =
    case Substring.getc substr of
	NONE               => NONE
      | SOME(#".",substr') => 
	(case parseDigits substr' of
	     (n,substr'') => SOME (Local n,substr''))
      | SOME(c,substr')    =>				
	if Char.isAlpha c then
	    case Substring.splitl isLabelChar substr of
		(label,substr'') =>
		SOME (Global (Substring.string label),substr'')
        else NONE

structure GlobalMap = SplayMapFn(struct
				 type ord_key = string
				 val compare = String.compare
				 end)

structure LocalMap = SplayMapFn(struct
				type ord_key = int
				val compare = Int.compare
				end)

type source = {file:string,line:int,address:int}
type globalMap = source GlobalMap.map;
type localMap = source LocalMap.map;
type map = {localMap:localMap,globalMap:globalMap}

exception RedefinedLabel of t * source

val fresh = {localMap=LocalMap.empty,globalMap=GlobalMap.empty}

fun lookup' (map,label) =
    case (map,label) of
	({localMap,globalMap},Global str) => GlobalMap.find (globalMap,str)
      | ({localMap,globalMap},Local n)  => LocalMap.find (localMap,n)

fun lookup (map:map,label:t) =
    case lookup' (map,label) of
	SOME {address=a,...} => SOME a
      | NONE => NONE

fun update (map,label,source) =
    case (map,label) of
	({localMap,globalMap},Global str) => 
	{localMap=LocalMap.empty,globalMap=GlobalMap.insert (globalMap,str,source)}
      | ({localMap,globalMap},Local n) =>  
	{localMap=(LocalMap.insert (localMap,n,source)),globalMap=globalMap}
					      
fun add (map,label,source) =
    case lookup' (map,label) of
	SOME source => raise RedefinedLabel(label,source)
      | NONE => update (map,label,source)
  
end

